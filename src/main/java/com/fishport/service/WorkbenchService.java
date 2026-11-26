package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一工作台：船主、港口调度员、执法人员、卸货员从同一服务确认当前责任与下一步操作。
 */
@Service
public class WorkbenchService {

    private final VoyageRepository voyageRepo;
    private final UnloadRecordRepository recordRepo;
    private final DisputeRepository disputeRepo;
    private final AppointmentRepository appointmentRepo;
    private final VesselRepository vesselRepo;
    private final CrewRosterService crewRosterService;

    public WorkbenchService(VoyageRepository voyageRepo, UnloadRecordRepository recordRepo,
                            DisputeRepository disputeRepo, AppointmentRepository appointmentRepo,
                            VesselRepository vesselRepo, CrewRosterService crewRosterService) {
        this.voyageRepo = voyageRepo;
        this.recordRepo = recordRepo;
        this.disputeRepo = disputeRepo;
        this.appointmentRepo = appointmentRepo;
        this.vesselRepo = vesselRepo;
        this.crewRosterService = crewRosterService;
    }

    public record TaskItem(String category, String reference, String responsibility, String nextAction) {
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<TaskItem> tasksFor(String role) {
        return switch (role) {
            case "OWNER" -> ownerTasks();
            case "DISPATCHER" -> dispatcherTasks();
            case "ENFORCEMENT" -> enforcementTasks();
            case "UNLOADER" -> unloaderTasks();
            default -> new ArrayList<>();
        };
    }

    /** 船主：自己航次的当前状态与下一步（离港/返港/关注排队/等待结案） */
    private List<TaskItem> ownerTasks() {
        List<TaskItem> tasks = new ArrayList<>();
        for (Voyage v : voyageRepo.findAll()) {
            if (!v.isOpen()) {
                continue;
            }
            String vesselNo = vesselRepo.findById(v.getVesselId()).map(Vessel::getVesselNo).orElse("?");
            String next = switch (v.getState()) {
                case APPROVED -> "许可已批复：安排离港（POST /api/voyages/" + v.getVoyageNo() + "/depart）";
                case DEPARTED -> "海上作业中：返港时上报到港（POST /api/voyages/" + v.getVoyageNo() + "/arrive）";
                case RETURNED -> "已到港：预约卸港时段并等待叫号";
                case UNLOADING -> "卸货中：配合称重与签收";
                case REWEIGHING -> "复称中：等待地磅复称结果";
                case CLOSED -> "已结案";
            };
            tasks.add(new TaskItem("航次", v.getVoyageNo() + "（船 " + vesselNo + "，状态 " + v.getState() + "）",
                    "跟踪本航次进度", next));
        }
        // N-5：船东看待放行名册及责任接收方，负责催办
        for (var t : crewRosterService.pendingReleaseTasks()) {
            tasks.add(new TaskItem("名册待放行", String.valueOf(t.get("revisionNo")),
                    String.valueOf(t.get("responsibility")), String.valueOf(t.get("nextAction"))));
        }
        return tasks;
    }

    /** 调度员：待排定/已排定的预约 */
    private List<TaskItem> dispatcherTasks() {
        List<TaskItem> tasks = new ArrayList<>();
        for (Appointment a : appointmentRepo.findAll()) {
            if (a.getStatus() == Types.ApptStatus.DONE || a.getStatus() == Types.ApptStatus.CANCELLED) {
                continue;
            }
            String next = switch (a.getStatus()) {
                case BOOKED -> "待排定：执行排队计算（POST /api/appointments/compute）";
                case SCHEDULED -> "已排定序号 " + a.getSeq() + "：等待到港打卡";
                case CHECKED_IN -> "已打卡：关注卸货进度，迟到时确认改期通知";
                case RESCHEDULED -> "已改期：确认船主收到新时段通知";
                default -> "-";
            };
            tasks.add(new TaskItem("预约", a.getAppointmentNo() + "（" + a.getMode() + "）",
                    "维护卸货线排队与通知", next));
        }
        // N-5：港口调度看待放行名册，港口方未确认时责任在港口
        for (var t : crewRosterService.pendingReleaseTasks()) {
            tasks.add(new TaskItem("名册待放行", String.valueOf(t.get("revisionNo")),
                    String.valueOf(t.get("responsibility")), String.valueOf(t.get("nextAction"))));
        }
        return tasks;
    }

    /** 执法人员：未办结争议 */
    private List<TaskItem> enforcementTasks() {
        List<TaskItem> tasks = new ArrayList<>();
        for (Dispute d : disputeRepo.findByStatus(Types.DisputeStatus.OPEN)) {
            tasks.add(new TaskItem("争议", "#" + d.getId() + " " + d.getType() + "（航次ID " + d.getVoyageId() + "）",
                    "现场核查并办结（POST /api/disputes/" + d.getId() + "/resolve）",
                    d.getDescription() != null ? d.getDescription() : "待核查"));
        }
        return tasks;
    }

    /** 卸货员：待称重/待签收的卸货记录 */
    private List<TaskItem> unloaderTasks() {
        List<TaskItem> tasks = new ArrayList<>();
        for (UnloadRecord r : recordRepo.findAll()) {
            String next = switch (r.getStatus()) {
                case DECLARED -> "待称重：安排过磅（地磅上传票据）";
                case WEIGHED, REWEIGHED -> "待签收：确认重量后签收（POST /api/unload-records/" + r.getId() + "/sign）";
                case DISPUTED -> "重量争议中：等待复称";
                case SIGNED -> "已签收";
            };
            if (r.getStatus() != Types.UnloadStatus.SIGNED) {
                tasks.add(new TaskItem("卸货", "#" + r.getId() + " " + r.getSpeciesCode()
                        + " 申报 " + r.getDeclaredKg() + "kg", "分物种卸货与签收", next));
            }
        }
        return tasks;
    }
}
