package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 许可服务：船主提交申请后同步校验。
 * 校验必须同时通过：有效期、海区、季节、渔具限制、物种额度；
 * 物种额度严格按 海区+物种+年度 单账核对，不得跨海区或跨物种合并剩余额度。
 * 同一船存在未结航次时不得再获第二张出海许可。
 */
@Service
public class PermitService {

    private final VesselRepository vesselRepo;
    private final FishingAreaRepository areaRepo;
    private final SpeciesRepository speciesRepo;
    private final SeasonRuleRepository seasonRepo;
    private final GearRestrictionRepository gearRepo;
    private final QuotaAccountRepository quotaRepo;
    private final PermitApplicationRepository applicationRepo;
    private final PermitRepository permitRepo;
    private final VoyageRepository voyageRepo;
    private final VoyageEventRepository eventRepo;
    private final QuotaService quotaService;
    private final RegulatorService regulatorService;

    public PermitService(VesselRepository vesselRepo, FishingAreaRepository areaRepo,
                         SpeciesRepository speciesRepo, SeasonRuleRepository seasonRepo,
                         GearRestrictionRepository gearRepo, QuotaAccountRepository quotaRepo,
                         PermitApplicationRepository applicationRepo, PermitRepository permitRepo,
                         VoyageRepository voyageRepo, VoyageEventRepository eventRepo,
                         QuotaService quotaService, RegulatorService regulatorService) {
        this.vesselRepo = vesselRepo;
        this.areaRepo = areaRepo;
        this.speciesRepo = speciesRepo;
        this.seasonRepo = seasonRepo;
        this.gearRepo = gearRepo;
        this.quotaRepo = quotaRepo;
        this.applicationRepo = applicationRepo;
        this.permitRepo = permitRepo;
        this.voyageRepo = voyageRepo;
        this.eventRepo = eventRepo;
        this.quotaService = quotaService;
        this.regulatorService = regulatorService;
    }

    public record ApplyResult(PermitApplication application, Permit permit, Voyage voyage, List<String> rejectReasons) {
    }

    /** 提交申请并同步校验：通过则批复发证、建航次、保留额度；不通过则记录全部驳回原因 */
    @Transactional
    public ApplyResult apply(PermitApplication app, String operator) {
        Vessel vessel = vesselRepo.findById(app.getVesselId())
                .orElseThrow(() -> BizException.notFound("船只不存在: " + app.getVesselId()));
        app.setApplicationNo("APP-" + shortId());
        app.setCreatedBy(operator);
        app.setCreatedAt(LocalDateTime.now());

        List<String> failures = validate(app, vessel);
        if (!failures.isEmpty()) {
            app.setStatus(Types.ApplicationStatus.REJECTED);
            app.setRejectReasons(String.join("；", failures));
            app.setReviewedAt(LocalDateTime.now());
            applicationRepo.save(app);
            return new ApplyResult(app, null, null, failures);
        }

        // 批复：发证 + 建航次 + 保留额度 + 上报监管
        app.setStatus(Types.ApplicationStatus.APPROVED);
        app.setReviewedAt(LocalDateTime.now());
        applicationRepo.save(app);

        Permit permit = new Permit();
        permit.setPermitNo("PMT-" + shortId());
        permit.setApplicationId(app.getId());
        permit.setVesselId(vessel.getId());
        permit.setAreaCode(app.getAreaCode());
        permit.setGearType(app.getGearType());
        permit.setValidFrom(app.getPlannedStart());
        permit.setValidTo(app.getPlannedEnd());
        permit.setStatus(Types.PermitStatus.ACTIVE);
        for (PermitApplicationItem item : app.getItems()) {
            permit.getItems().add(new PermitItem(item.getSpeciesCode(), item.getDeclaredLimitKg()));
        }
        permitRepo.save(permit);

        Voyage voyage = new Voyage();
        voyage.setVoyageNo("VOY-" + shortId());
        voyage.setPermitId(permit.getId());
        voyage.setVesselId(vessel.getId());
        voyage.setState(Types.VoyageState.APPROVED);
        voyageRepo.save(voyage);

        int year = app.getPlannedStart().getYear();
        for (PermitItem item : permit.getItems()) {
            quotaService.reserve(permit.getId(), voyage.getId(), permit.getAreaCode(),
                    item.getSpeciesCode(), year, item.getLimitKg());
        }

        recordEvent(voyage.getId(), permit.getId(), Types.VoyageEventType.PERMIT_ISSUED,
                "许可 " + permit.getPermitNo() + " 批复，航次 " + voyage.getVoyageNo() + " 建立", operator,
                "PERMIT_ISSUED:" + permit.getId());
        regulatorService.report(Types.ReceiptEventType.PERMIT_ISSUED, voyage.getId(), permit.getId(),
                "permitNo=" + permit.getPermitNo() + ",vessel=" + vessel.getVesselNo());

        return new ApplyResult(app, permit, voyage, List.of());
    }

    /** 五项校验 + 未结航次检查，返回全部失败原因（空列表表示通过） */
    private List<String> validate(PermitApplication app, Vessel vessel) {
        List<String> failures = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 0. 同一船未结航次不得再获第二张出海许可
        voyageRepo.lockOpenByVesselId(vessel.getId()).ifPresent(open -> failures.add(
                "船 " + vessel.getVesselNo() + " 存在未结航次 " + open.getVoyageNo() + "，不得再获第二张出海许可"));

        // 1. 有效期：计划窗口合法且未过期
        if (app.getPlannedStart() == null || app.getPlannedEnd() == null
                || !app.getPlannedEnd().isAfter(app.getPlannedStart())) {
            failures.add("有效期校验失败：计划作业时间窗口不合法");
        } else if (!app.getPlannedEnd().isAfter(now)) {
            failures.add("有效期校验失败：计划作业结束时间已过，许可有效期无法覆盖");
        }

        // 2. 海区：存在且开放
        var area = areaRepo.findById(app.getAreaCode());
        if (area.isEmpty() || !area.get().isActive()) {
            failures.add("海区校验失败：海区 " + app.getAreaCode() + " 不存在或未开放");
        }

        // 3. 渔具限制
        if (area.isPresent() && area.get().isActive()) {
            var gear = gearRepo.findByAreaCodeAndGearType(app.getAreaCode(), app.getGearType());
            if (gear.isEmpty()) {
                failures.add("渔具校验失败：海区 " + app.getAreaCode() + " 未登记渔具 " + app.getGearType() + " 的使用许可");
            } else if (!gear.get().isAllowed()) {
                failures.add("渔具校验失败：海区 " + app.getAreaCode() + " 禁用渔具 " + app.getGearType());
            }
        }

        // 4./5. 逐物种：季节 + 额度（严格单账核对，不跨海区/物种合并）
        if (app.getItems() == null || app.getItems().isEmpty()) {
            failures.add("申请必须包含至少一个目标物种及申报捕捞上限");
        } else if (app.getPlannedStart() != null && app.getPlannedEnd() != null) {
            int year = app.getPlannedStart().getYear();
            for (PermitApplicationItem item : app.getItems()) {
                String sp = item.getSpeciesCode();
                var species = speciesRepo.findById(sp);
                if (species.isEmpty() || !species.get().isActive()) {
                    failures.add("物种校验失败：物种 " + sp + " 不存在或未开放");
                    continue;
                }
                if (item.getDeclaredLimitKg() == null || item.getDeclaredLimitKg().signum() <= 0) {
                    failures.add("物种 " + sp + " 申报捕捞上限必须为正数");
                    continue;
                }
                // 季节：计划窗口不得落入禁渔期
                for (SeasonRule rule : seasonRepo.findByAreaCodeAndSpeciesCode(app.getAreaCode(), sp)) {
                    if (windowIntersectsClosedSeason(app.getPlannedStart().toLocalDate(),
                            app.getPlannedEnd().toLocalDate(), rule.getClosedFrom(), rule.getClosedTo())) {
                        failures.add("季节校验失败：" + sp + " 在海区 " + app.getAreaCode() + " 的禁渔期 "
                                + rule.getClosedFrom() + "~" + rule.getClosedTo() + " 与计划作业时间重叠");
                    }
                }
                // 额度：仅看本海区+本物种+本年度账户的可用额度
                var account = quotaRepo.findByAreaCodeAndSpeciesCodeAndSeasonYear(app.getAreaCode(), sp, year);
                if (account.isEmpty()) {
                    failures.add("额度校验失败：海区 " + app.getAreaCode() + " 物种 " + sp + " 年度 " + year + " 无额度账");
                } else {
                    BigDecimal available = account.get().available();
                    if (available.compareTo(item.getDeclaredLimitKg()) < 0) {
                        failures.add("额度校验失败：海区 " + app.getAreaCode() + " 物种 " + sp + " 可用额度 "
                                + available + "kg 小于申报 " + item.getDeclaredLimitKg()
                                + "kg（不同海区或物种的剩余额度不得相加）");
                    }
                }
            }
        }
        return failures;
    }

    /** 判断计划窗口是否与禁渔期（MM-dd 区间，支持跨年）重叠 */
    static boolean windowIntersectsClosedSeason(LocalDate start, LocalDate end, String closedFrom, String closedTo) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String md = d.format(fmt);
            boolean inClosed;
            if (closedFrom.compareTo(closedTo) <= 0) {
                inClosed = md.compareTo(closedFrom) >= 0 && md.compareTo(closedTo) <= 0;
            } else { // 跨年区间，如 12-01 ~ 02-28
                inClosed = md.compareTo(closedFrom) >= 0 || md.compareTo(closedTo) <= 0;
            }
            if (inClosed) {
                return true;
            }
        }
        return false;
    }

    private void recordEvent(Long voyageId, Long permitId, Types.VoyageEventType type, String detail,
                             String actor, String requestKey) {
        VoyageEvent e = new VoyageEvent();
        e.setVoyageId(voyageId);
        e.setPermitId(permitId);
        e.setEventType(type);
        e.setDetail(detail);
        e.setActor(actor);
        e.setRequestKey(requestKey);
        eventRepo.save(e);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public PermitApplication getApplication(String applicationNo) {
        return applicationRepo.findByApplicationNo(applicationNo)
                .orElseThrow(() -> BizException.notFound("申请不存在: " + applicationNo));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Permit getPermit(String permitNo) {
        return permitRepo.findByPermitNo(permitNo)
                .orElseThrow(() -> BizException.notFound("许可不存在: " + permitNo));
    }

    static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
