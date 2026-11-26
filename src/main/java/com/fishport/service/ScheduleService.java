package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 卸港预约调度：港口只有一条卸货线、一台地磅与有限冷库，作业间清场 cleanupMin 分钟。
 * FIXED  固定时段：按已通知时段分配，迟到会占住空档；迟到到无法按期完成则改期到队尾并重新通知。
 * ROLLING 滚动叫号：按实际到港先后与剩余作业时间滚动排序，会改变已公布顺序并重新通知。
 */
@Service
public class ScheduleService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepo;
    private final PortResourceRepository portRepo;
    private final VoyageRepository voyageRepo;
    private final NotificationRepository notificationRepo;

    public ScheduleService(AppointmentRepository appointmentRepo, PortResourceRepository portRepo,
                           VoyageRepository voyageRepo, NotificationRepository notificationRepo) {
        this.appointmentRepo = appointmentRepo;
        this.portRepo = portRepo;
        this.voyageRepo = voyageRepo;
        this.notificationRepo = notificationRepo;
    }

    /** 预约登记：校验冷库容量（同一时段在港卸货总量不得超过冷库容量） */
    @Transactional
    public Appointment book(String voyageNo, String portCode, Types.ApptMode mode,
                            LocalDateTime requestedStart, LocalDateTime plannedArrival,
                            int durationMin, LocalDateTime latestFinish, BigDecimal expectedUnloadKg) {
        Voyage voyage = voyageRepo.findByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
        if (!voyage.isOpen()) {
            throw BizException.conflict("VOYAGE_CLOSED", "航次已结案，不得预约");
        }
        PortResource port = portRepo.findByCode(portCode)
                .orElseThrow(() -> BizException.notFound("港口不存在: " + portCode));
        if (durationMin <= 0) {
            throw BizException.badRequest("卸货时长必须为正数");
        }
        if (latestFinish == null) {
            throw BizException.badRequest("必须给出最晚完成时间");
        }
        if (mode == Types.ApptMode.FIXED && requestedStart == null) {
            throw BizException.badRequest("固定时段模式必须给出申请时段起点 requestedStart");
        }
        if (mode == Types.ApptMode.ROLLING && plannedArrival == null) {
            throw BizException.badRequest("滚动叫号模式必须给出计划到港时间 plannedArrival");
        }
        var active = appointmentRepo.findByVoyageIdAndStatusNotIn(voyage.getId(),
                List.of(Types.ApptStatus.CANCELLED, Types.ApptStatus.DONE));
        if (!active.isEmpty()) {
            throw BizException.conflict("APPOINTMENT_EXISTS",
                    "航次已有进行中的预约 " + active.get(0).getAppointmentNo());
        }

        LocalDate serviceDate = (requestedStart != null ? requestedStart : plannedArrival).toLocalDate();
        LocalDateTime start = requestedStart != null ? requestedStart : plannedArrival;
        checkColdCapacity(port, serviceDate, start, durationMin, expectedUnloadKg, null);

        Appointment a = new Appointment();
        a.setAppointmentNo("APT-" + PermitService.shortId());
        a.setVoyageId(voyage.getId());
        a.setPortId(port.getId());
        a.setServiceDate(serviceDate);
        a.setMode(mode);
        a.setStatus(Types.ApptStatus.BOOKED);
        a.setRequestedStart(requestedStart);
        a.setPlannedArrival(plannedArrival);
        a.setDurationMin(durationMin);
        a.setCleanupMin(port.getCleanupMin());
        a.setLatestFinish(latestFinish);
        a.setExpectedUnloadKg(expectedUnloadKg != null ? expectedUnloadKg : BigDecimal.ZERO);
        return appointmentRepo.save(a);
    }

    /** 计算并公布排队：固定模式按申请时段，滚动模式按（实际/计划）到港先后 */
    @Transactional
    public List<Appointment> compute(String portCode, LocalDate date, Types.ApptMode mode) {
        PortResource port = portRepo.findByCode(portCode)
                .orElseThrow(() -> BizException.notFound("港口不存在: " + portCode));
        return recompute(port, date, mode);
    }

    /** 到港打卡：记录实际到港时间并重排（固定模式迟到可能改期，滚动模式改变已公布顺序） */
    @Transactional
    public Appointment checkin(String appointmentNo, LocalDateTime actualArrival) {
        Appointment a = appointmentRepo.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> BizException.notFound("预约不存在: " + appointmentNo));
        if (a.getStatus() == Types.ApptStatus.CANCELLED || a.getStatus() == Types.ApptStatus.DONE) {
            throw BizException.conflict("BAD_STATE", "预约状态 " + a.getStatus() + " 不得打卡");
        }
        a.setActualArrival(actualArrival);
        a.setStatus(Types.ApptStatus.CHECKED_IN);
        appointmentRepo.save(a);
        PortResource port = portRepo.findById(a.getPortId()).orElseThrow();
        recompute(port, a.getServiceDate(), a.getMode());
        return appointmentRepo.findByAppointmentNo(appointmentNo).orElseThrow();
    }

    /** 卸货完成，释放卸货线 */
    @Transactional
    public Appointment finish(String appointmentNo) {
        Appointment a = appointmentRepo.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> BizException.notFound("预约不存在: " + appointmentNo));
        a.setStatus(Types.ApptStatus.DONE);
        return appointmentRepo.save(a);
    }

    // ---------------- 调度核心 ----------------

    private List<Appointment> recompute(PortResource port, LocalDate date, Types.ApptMode mode) {
        List<Appointment> appts = new ArrayList<>(appointmentRepo.findByPortIdAndServiceDate(port.getId(), date)
                .stream()
                .filter(a -> a.getMode() == mode)
                .filter(a -> a.getStatus() != Types.ApptStatus.CANCELLED && a.getStatus() != Types.ApptStatus.DONE)
                .toList());

        // 记录重排前的已公布槽位，用于判断是否需要重新通知
        Map<Long, LocalDateTime> oldStarts = new HashMap<>();
        Map<Long, Integer> oldSeqs = new HashMap<>();
        for (Appointment a : appts) {
            oldStarts.put(a.getId(), a.getSlotStart());
            oldSeqs.put(a.getId(), a.getSeq());
        }

        if (mode == Types.ApptMode.FIXED) {
            appts.sort(Comparator.comparing(Appointment::getRequestedStart));
        } else {
            appts.sort(Comparator.comparing((Appointment a) ->
                            a.getActualArrival() != null ? a.getActualArrival() : a.getPlannedArrival())
                    .thenComparing(Appointment::getLatestFinish)
                    .thenComparing(Appointment::getId));
        }

        LocalDateTime windowStart = date.atTime(LocalTime.parse(port.getOpenTime(), HM));
        List<Appointment> lateToTail = new ArrayList<>();
        assignSlots(appts, windowStart, lateToTail, mode);

        // 固定模式：迟到到无法按期完成的，改期到队尾并重新通知
        for (Appointment late : lateToTail) {
            appts.remove(late);
        }
        if (!lateToTail.isEmpty()) {
            assignSlots(appts, windowStart, new ArrayList<>(), mode);
            for (Appointment late : lateToTail) {
                appts.add(late);
            }
            assignSlots(appts, windowStart, new ArrayList<>(), mode);
            for (Appointment late : lateToTail) {
                late.setStatus(Types.ApptStatus.RESCHEDULED);
            }
        }

        int seq = 1;
        for (Appointment a : appts) {
            a.setSeq(seq++);
            if (a.getStatus() == Types.ApptStatus.BOOKED) {
                a.setStatus(Types.ApptStatus.SCHEDULED);
            }
            appointmentRepo.save(a);
            notifyIfChanged(a, oldStarts.get(a.getId()), oldSeqs.get(a.getId()),
                    lateToTail.contains(a));
        }
        return appts;
    }

    /** 依次分配槽位：下一作业起点 = max(可开始时间, 上一作业结束 + 清场时间) */
    private void assignSlots(List<Appointment> ordered, LocalDateTime windowStart,
                             List<Appointment> lateToTail, Types.ApptMode mode) {
        LocalDateTime prevEndWithCleanup = windowStart;
        for (Appointment a : ordered) {
            LocalDateTime earliest = earliestStart(a, mode);
            LocalDateTime start = earliest.isAfter(prevEndWithCleanup) ? earliest : prevEndWithCleanup;
            LocalDateTime end = start.plusMinutes(a.getDurationMin());
            a.setSlotStart(start);
            a.setSlotEnd(end);
            boolean breach = end.isAfter(a.getLatestFinish());
            a.setDeadlineBreach(breach);
            if (breach && mode == Types.ApptMode.FIXED && a.getActualArrival() != null
                    && a.getStatus() != Types.ApptStatus.RESCHEDULED) {
                lateToTail.add(a); // 迟到导致无法按期完成，待改期队尾
            }
            prevEndWithCleanup = end.plusMinutes(a.getCleanupMin());
        }
    }

    private LocalDateTime earliestStart(Appointment a, Types.ApptMode mode) {
        if (mode == Types.ApptMode.FIXED) {
            // 固定时段：按已通知时段；迟到则从实际到港时刻起算（迟到占住空档）
            if (a.getActualArrival() != null && a.getActualArrival().isAfter(a.getRequestedStart())) {
                return a.getActualArrival();
            }
            return a.getRequestedStart();
        }
        // 滚动叫号：按实际到港（未到港按计划到港）
        return a.getActualArrival() != null ? a.getActualArrival() : a.getPlannedArrival();
    }

    private void notifyIfChanged(Appointment a, LocalDateTime oldStart, Integer oldSeq, boolean rescheduled) {
        String slot = a.getSlotStart().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                + "~" + a.getSlotEnd().toLocalTime().format(HM);
        if (rescheduled) {
            notificationRepo.save(new Notification(a.getId(), a.getVoyageId(), Types.NotificationType.RESCHEDULED,
                    "预约 " + a.getAppointmentNo() + " 迟到改期，新时段 " + slot + "，序号 " + a.getSeq()
                            + (a.isDeadlineBreach() ? "（将超过最晚完成时间）" : "")));
        } else if (oldStart == null) {
            notificationRepo.save(new Notification(a.getId(), a.getVoyageId(), Types.NotificationType.SCHEDULED,
                    "预约 " + a.getAppointmentNo() + " 已排定：时段 " + slot + "，序号 " + a.getSeq()
                            + (a.isDeadlineBreach() ? "（预警：将超过最晚完成时间）" : "")));
        } else if (!oldStart.equals(a.getSlotStart()) || !Objects.equals(oldSeq, a.getSeq())) {
            notificationRepo.save(new Notification(a.getId(), a.getVoyageId(), Types.NotificationType.QUEUE_CHANGED,
                    "预约 " + a.getAppointmentNo() + " 排队变化：新时段 " + slot + "，序号 " + a.getSeq()));
        }
    }

    /** 冷库容量校验：同时段在港卸货总量不得超过冷库容量 */
    private void checkColdCapacity(PortResource port, LocalDate date, LocalDateTime start, int durationMin,
                                   BigDecimal expectedKg, Long excludeId) {
        if (expectedKg == null || expectedKg.signum() == 0) {
            return;
        }
        LocalDateTime end = start.plusMinutes(durationMin);
        BigDecimal overlapping = BigDecimal.ZERO;
        for (Appointment other : appointmentRepo.findByPortIdAndServiceDate(port.getId(), date)) {
            if (other.getStatus() == Types.ApptStatus.CANCELLED || other.getStatus() == Types.ApptStatus.DONE
                    || other.getId().equals(excludeId)) {
                continue;
            }
            LocalDateTime otherStart = other.getSlotStart() != null ? other.getSlotStart()
                    : other.getRequestedStart() != null ? other.getRequestedStart() : other.getPlannedArrival();
            if (otherStart == null) {
                continue;
            }
            LocalDateTime otherEnd = otherStart.plusMinutes(other.getDurationMin());
            if (otherStart.isBefore(end) && start.isBefore(otherEnd)) {
                overlapping = overlapping.add(other.getExpectedUnloadKg());
            }
        }
        if (overlapping.add(expectedKg).compareTo(port.getColdCapacityKg()) > 0) {
            throw BizException.unprocessable("COLD_STORAGE_FULL",
                    "冷库容量不足：该时段已占 " + overlapping + "kg，新增 " + expectedKg
                            + "kg 超过容量 " + port.getColdCapacityKg() + "kg");
        }
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Appointment> listByPortAndDate(String portCode, LocalDate date) {
        PortResource port = portRepo.findByCode(portCode)
                .orElseThrow(() -> BizException.notFound("港口不存在: " + portCode));
        return appointmentRepo.findByPortIdAndServiceDate(port.getId(), date).stream()
                .sorted(Comparator.comparing(Appointment::getSeq, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Notification> notificationsOf(Long voyageId) {
        return notificationRepo.findByVoyageIdOrderByCreatedAtAsc(voyageId);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Appointment getByNo(String appointmentNo) {
        return appointmentRepo.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> BizException.notFound("预约不存在: " + appointmentNo));
    }
}
