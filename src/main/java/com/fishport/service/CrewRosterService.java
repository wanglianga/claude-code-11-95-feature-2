package com.fishport.service;

import com.fishport.domain.*;
import com.fishport.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 船员名册替换服务（N-5）。
 *
 * 确认模型：字段投影绑定（本系统唯一实现，不提供整单/投影切换）。
 * 港口、保险、应急三方各持一个协议版本化的字段投影；重签时仅当投影内容哈希与协议版本逐项一致，
 * 旧确认才可沿用（CARRIED），否则旧确认置 INVALIDATED 并记录失效原因，该方必须重新签认。
 * 严禁新名册配旧船长确认：修订号不同的确认永不直接放行。
 *
 * 闸口规则：只接受当时有效（RELEASED 且凭证 ACTIVE 且窗口未关）的船员版本；
 * 替换申请逐人核岗位、船型权限、证书、休息、航次职责，离港闸按整份名册重核岗位覆盖；
 * 重签即撤销旧二维码，新证在三方确认齐备后签发；离港后的迟到签到只留核查，不回滚新名册。
 */
@Service
public class CrewRosterService {

    /** 投影协议版本：协议升级会使全部旧确认失效 */
    public static final String PROTOCOL_VERSION = "1.0";

    private static final List<Types.RosterParty> PARTIES =
            List.of(Types.RosterParty.PORT, Types.RosterParty.INSURER, Types.RosterParty.EMERGENCY);

    /** 各方投影字段（协议内容）：应急电话仅在应急方投影内，港口与保险投影不含该字段 */
    private static final Map<Types.RosterParty, List<String>> PROJECTION_FIELDS = Map.of(
            Types.RosterParty.PORT, List.of("voyageId", "vesselNo", "vesselType",
                    "personNo", "name", "positionCode", "voyageDuty", "certificateNo", "certValidTo",
                    "vesselTypeGrants", "qualificationCodes"),
            Types.RosterParty.INSURER, List.of("voyageId", "vesselNo",
                    "personNo", "name", "positionCode", "voyageDuty", "certificateNo", "qualificationCodes"),
            Types.RosterParty.EMERGENCY, List.of("voyageId", "vesselNo",
                    "personNo", "name", "positionCode", "voyageDuty", "emergencyPhone"));

    private final CrewRosterRevisionRepository revisionRepo;
    private final CrewMemberRepository memberRepo;
    private final CrewPersonRepository personRepo;
    private final OwnerDecisionRepository decisionRepo;
    private final RosterAcknowledgementRepository ackRepo;
    private final GateCredentialRepository credentialRepo;
    private final DepartureWindowRepository windowRepo;
    private final GateScanLogRepository scanRepo;
    private final VoyageRepository voyageRepo;
    private final VesselRepository vesselRepo;
    private final VoyageEventRepository eventRepo;
    private final SimClock clock;

    public CrewRosterService(CrewRosterRevisionRepository revisionRepo, CrewMemberRepository memberRepo,
                             CrewPersonRepository personRepo, OwnerDecisionRepository decisionRepo,
                             RosterAcknowledgementRepository ackRepo, GateCredentialRepository credentialRepo,
                             DepartureWindowRepository windowRepo, GateScanLogRepository scanRepo,
                             VoyageRepository voyageRepo, VesselRepository vesselRepo,
                             VoyageEventRepository eventRepo, SimClock clock) {
        this.revisionRepo = revisionRepo;
        this.memberRepo = memberRepo;
        this.personRepo = personRepo;
        this.decisionRepo = decisionRepo;
        this.ackRepo = ackRepo;
        this.credentialRepo = credentialRepo;
        this.windowRepo = windowRepo;
        this.scanRepo = scanRepo;
        this.voyageRepo = voyageRepo;
        this.vesselRepo = vesselRepo;
        this.eventRepo = eventRepo;
        this.clock = clock;
    }

    // ---------------- 结果 DTO ----------------

    public record ProjectionView(String party, String protocolVersion, String hash, List<String> fields) {
    }

    public record AckView(Long id, String ackNo, Types.RosterParty party, Types.AcknowledgementStatus status,
                          String projectionVersion, String projectionHash, List<String> projectionFields,
                          Long carriedFromAckId, String confirmedBy, LocalDateTime confirmedAt,
                          String invalidatedReason) {
    }

    public record ReSignResult(CrewRosterRevision revision, OwnerDecision decision,
                               List<AckView> acknowledgements, int reSignRequired, int carried,
                               List<String> revokedQrCodes, boolean releasable,
                               List<Types.RosterParty> pendingParties) {
    }

    public record GateScanResult(GateScanLog log, Types.GateScanVerdict verdict, String reasonCode,
                                 String detail, boolean afterDeparture, String effectiveRevisionNo,
                                 List<Types.RosterParty> pendingParties) {
    }

    public record WindowView(DepartureWindow window, boolean available) {
    }

    public record RosterStatusView(CrewRosterRevision current, List<CrewMember> members,
                                   List<AckView> acknowledgements, boolean releasable,
                                   List<Types.RosterParty> pendingParties, GateCredential activeCredential,
                                   List<WindowView> windows, List<String> coverageIssues) {
    }

    // ---------------- 初版名册 ----------------

    /** 许可批复后建立初版名册（DRAFT→PENDING_RELEASE，三方待签） */
    @Transactional
    public CrewRosterRevision initializeRoster(String voyageNo, List<String> personNos,
                                               List<String> requiredQualifications, String reason, String actor) {
        Voyage voyage = requireVoyage(voyageNo);
        revisionRepo.lockByVoyageId(voyage.getId());
        if (revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId()).isPresent()) {
            throw BizException.conflict("ROSTER_EXISTS", "航次 " + voyageNo + " 已存在名册，不得重复初始化");
        }
        List<CrewPerson> persons = loadPersons(personNos);
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        // 初版只做结构性岗位覆盖核验；休息达标在离港扫码时刻按整份名册重核
        List<String> coverage = validateWholeRoster(persons, vessel, requiredQualifications, now(), false);
        if (!coverage.isEmpty()) {
            throw BizException.unprocessable("ROSTER_COVERAGE_FAIL", "初版名册岗位覆盖核验失败：" + String.join("；", coverage));
        }
        CrewRosterRevision rev = newRevision(voyage, 1, null, Types.RosterChangeKind.INITIAL, reason,
                requiredQualifications);
        attachSnapshots(rev, persons, Map.of());
        revisionRepo.save(rev);
        createAcks(rev, vessel, voyage, null);
        recordEvent(voyage, Types.VoyageEventType.ROSTER_REVISION,
                "初版名册 " + rev.getRevisionNo() + " 建立，等待三方投影确认", actor);
        return rev;
    }

    // ---------------- 船东决定 + 重签 ----------------

    /** 选择一：原船长休息达标后继续（船员关系与已发名册人员不变，新修订逐投影核对，未变化的确认沿用） */
    @Transactional
    public ReSignResult keepCaptainRest(String voyageNo, String reason, String actor) {
        Voyage voyage = requireVoyage(voyageNo);
        CrewRosterRevision current = lockCurrent(voyage);
        CrewPerson captain = masterOf(current, "CAPTAIN");
        if (captain.getRestReadyAt().isAfter(now())) {
            throw BizException.conflict("REST_NOT_READY",
                    "原船长休息尚未达标（达标时刻 " + captain.getRestReadyAt() + "），不能按休息达标重签");
        }
        OwnerDecision decision = saveDecision(voyage, Types.OwnerDecisionType.KEEP_CAPTAIN_REST, reason, actor);
        return reSign(voyage, current, snapshotPersonsOf(current), Types.RosterChangeKind.REST, reason,
                decision, Map.of(), actor);
    }

    /** 选择二：启用替补（核岗位、船型权限、证书、休息、航次职责），09:30 完成重签示例 */
    @Transactional
    public ReSignResult useSubstitute(String voyageNo, String replacementPersonNo, String reason, String actor) {
        Voyage voyage = requireVoyage(voyageNo);
        CrewRosterRevision current = lockCurrent(voyage);
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        CrewPerson replacement = personRepo.findByPersonNo(replacementPersonNo)
                .orElseThrow(() -> BizException.notFound("人员不存在: " + replacementPersonNo));

        List<String> failures = validateReplacement(current, replacement, vessel, now());
        if (!failures.isEmpty()) {
            throw BizException.unprocessable("SUBSTITUTE_REJECTED",
                    "替补核验未通过：" + String.join("；", failures));
        }
        // 以同岗位替换：找到被替换的同岗在值人员
        List<CrewMember> currentMembers = memberRepo.findByRevisionId(current.getId());
        CrewMember replaced = currentMembers.stream()
                .filter(m -> m.getPositionCode().equals(replacement.getPositionCode()))
                .findFirst()
                .orElseThrow(() -> BizException.unprocessable("POSITION_GAP",
                        "名册中无 " + replacement.getPositionCode() + " 岗位，替补无法对位"));

        OwnerDecision decision = saveDecision(voyage, Types.OwnerDecisionType.USE_SUBSTITUTE, reason, actor);
        List<CrewPerson> nextPersons = new ArrayList<>();
        for (CrewMember m : currentMembers) {
            nextPersons.add(m.getPersonNo().equals(replaced.getPersonNo())
                    ? replacement : personRepo.findByPersonNo(m.getPersonNo()).orElseThrow());
        }
        return reSign(voyage, current, nextPersons, Types.RosterChangeKind.SUBSTITUTE, reason,
                decision, Map.of(), actor);
    }

    /** 名册更正：可给整份新人员清单（如轮机员请假换人），可仅覆盖应急电话；按投影依赖决定失效范围 */
    @Transactional
    public ReSignResult correctRoster(String voyageNo, List<String> nextPersonNos,
                                      Map<String, String> emergencyPhoneByPerson,
                                      String reason, String actor) {
        Voyage voyage = requireVoyage(voyageNo);
        CrewRosterRevision current = lockCurrent(voyage);
        OwnerDecision decision = saveDecision(voyage, Types.OwnerDecisionType.CORRECT_ROSTER, reason, actor);
        List<CrewPerson> nextPersons = (nextPersonNos == null || nextPersonNos.isEmpty())
                ? snapshotPersonsOf(current) : loadPersons(nextPersonNos);
        Map<String, String> overrides = emergencyPhoneByPerson == null ? Map.of() : emergencyPhoneByPerson;
        return reSign(voyage, current, nextPersons, Types.RosterChangeKind.CORRECTION, reason,
                decision, overrides, actor);
    }

    /** 人员请假/复职：请假后若其持有唯一专项资质，下一次重签的整名册覆盖核验会失败 */
    @Transactional
    public CrewPerson setPersonActive(String personNo, boolean active) {
        CrewPerson p = personRepo.findByPersonNo(personNo)
                .orElseThrow(() -> BizException.notFound("人员不存在: " + personNo));
        p.setActive(active);
        return personRepo.save(p);
    }

    /**
     * 离港前置校验：航次一旦建立船员名册，当前名册必须已放行、整份名册休息/证书/船型/资质
     * 在离港时刻仍然达标。未建档名册的旧流程走原有离港路径（N-5 场景均会建档，强约束不受影响）。
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void assertDepartureReady(Voyage voyage) {
        var currentOpt = revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId());
        if (currentOpt.isEmpty()) {
            return; // 未纳入名册管理的旧流程，不在 N-5 闸口约束内
        }
        CrewRosterRevision current = currentOpt.get();
        if (current.getStatus() != Types.RosterStatus.RELEASED) {
            List<Types.RosterParty> pending = pendingParties(
                    toViews(ackRepo.findByRevisionIdOrderByPartyAsc(current.getId())));
            throw BizException.conflict("ROSTER_NOT_RELEASED",
                    "名册 " + current.getRevisionNo() + " 待放行，未确认接收方 " + pending + "，不得离港");
        }
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        List<CrewMember> members = memberRepo.findByRevisionId(current.getId());
        List<String> issues = validateWholeRoster(members, vessel,
                splitCsv(current.getRequiredQualifications()), now(), true);
        if (!issues.isEmpty()) {
            throw BizException.conflict("ROSTER_COVERAGE_FAIL", "离港名册核验未通过：" + String.join("；", issues));
        }
    }

    private ReSignResult reSign(Voyage voyage, CrewRosterRevision previous, List<CrewPerson> nextPersons,
                                Types.RosterChangeKind kind, String reason, OwnerDecision decision,
                                Map<String, String> phoneOverrides, String actor) {
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        // 重签只核结构性岗位覆盖（船长唯一/证书/船型/专项资质）；
        // 休息达标由替补/休息重签入口显式核验，并在离港闸口按整份名册强制重核
        List<String> coverage = validateWholeRoster(nextPersons, vessel,
                splitCsv(previous.getRequiredQualifications()), now(), false);
        if (!coverage.isEmpty()) {
            throw BizException.unprocessable("ROSTER_COVERAGE_FAIL",
                    "整份名册岗位覆盖核验失败（不只查被替换行）：" + String.join("；", coverage));
        }

        CrewRosterRevision rev = newRevision(voyage, previous.getRevisionSeq() + 1, previous.getId(),
                kind, reason, splitCsv(previous.getRequiredQualifications()));
        rev.setOwnerDecisionId(decision.getId());
        attachSnapshots(rev, nextPersons, phoneOverrides);
        revisionRepo.save(rev);
        decision.setRevisionId(rev.getId());
        decisionRepo.save(decision);

        // 旧修订作废；旧二维码在重签时即撤销（新证待三方确认齐备后签发）
        previous.setStatus(Types.RosterStatus.SUPERSEDED);
        revisionRepo.save(previous);
        List<String> revokedQr = revokeActiveCredentials(voyage.getId(),
                "名册 " + rev.getRevisionNo() + " 重签，旧凭证同时撤销（待三方投影确认后签发新证）");

        List<AckView> acks = createAcks(rev, vessel, voyage, previous);
        rev.setSignedAt(now());
        boolean releasable = tryRelease(rev, voyage, acks);
        revisionRepo.save(rev);

        recordEvent(voyage, Types.VoyageEventType.ROSTER_REVISION,
                "名册重签 " + rev.getRevisionNo() + "（" + kind + "），待重签 "
                        + countPending(acks) + " 份、沿用 " + countCarried(acks) + " 份，撤销旧二维码 "
                        + revokedQr.size() + " 个", actor);
        return new ReSignResult(rev, decision, acks, countPending(acks), countCarried(acks),
                revokedQr, releasable, pendingParties(acks));
    }

    // ---------------- 接收方确认 ----------------

    /** 接收方对当前修订的投影确认；只能确认当前修订，旧修订不再接受签认 */
    @Transactional
    public RosterAcknowledgement confirmRevision(String revisionNo, Types.RosterParty party, String actor) {
        CrewRosterRevision current = revisionRepo.findByRevisionNo(revisionNo)
                .orElseThrow(() -> BizException.notFound("名册修订不存在: " + revisionNo));
        return confirmInternal(current, party, actor);
    }

    /** 按航次确认当前修订投影 */
    @Transactional
    public RosterAcknowledgement confirm(String voyageNo, Types.RosterParty party, String actor) {
        Voyage voyage = requireVoyage(voyageNo);
        CrewRosterRevision current = revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId())
                .orElseThrow(() -> BizException.notFound("航次无名册: " + voyageNo));
        return confirmInternal(current, party, actor);
    }

    private RosterAcknowledgement confirmInternal(CrewRosterRevision current, Types.RosterParty party, String actor) {
        Voyage voyage = voyageRepo.findById(current.getVoyageId()).orElseThrow();
        if (current.getStatus() == Types.RosterStatus.SUPERSEDED) {
            throw BizException.conflict("ROSTER_SUPERSEDED",
                    "修订 " + current.getRevisionNo() + " 已被取代，不能再签认，请确认最新修订");
        }
        RosterAcknowledgement ack = ackRepo.findByRevisionIdAndParty(current.getId(), party)
                .orElseThrow(() -> BizException.notFound("投影确认不存在"));
        if (ack.getStatus() == Types.AcknowledgementStatus.INVALIDATED) {
            throw BizException.conflict("ACK_INVALIDATED", "该确认已失效：" + ack.getInvalidatedReason());
        }
        if (ack.getStatus() != Types.AcknowledgementStatus.CONFIRMED) {
            ack.setStatus(Types.AcknowledgementStatus.CONFIRMED);
            ack.setConfirmedBy(actor);
            ack.setConfirmedAt(now());
            ackRepo.save(ack);
            recordEvent(voyage, Types.VoyageEventType.ROSTER_ACK,
                    party + " 确认投影 " + ack.getAckNo() + "（hash=" + ack.getProjectionHash().substring(0, 8) + "）",
                    actor);
        }
        List<AckView> views = toViews(ackRepo.findByRevisionIdOrderByPartyAsc(current.getId()));
        if (tryRelease(current, voyage, views)) {
            revisionRepo.save(current);
        }
        return ack;
    }

    /** 三方确认齐备：修订放行并签发新二维码，同时撤销任何残留旧凭证 */
    private boolean tryRelease(CrewRosterRevision rev, Voyage voyage, List<AckView> acks) {
        if (rev.getStatus() == Types.RosterStatus.RELEASED) {
            return true;
        }
        if (acks.stream().anyMatch(a -> a.status() != Types.AcknowledgementStatus.CONFIRMED)) {
            rev.setStatus(Types.RosterStatus.PENDING_RELEASE);
            return false;
        }
        rev.setStatus(Types.RosterStatus.RELEASED);
        rev.setReleasedAt(now());
        revokeActiveCredentials(voyage.getId(), "新名册 " + rev.getRevisionNo() + " 放行，旧凭证全部撤销");
        GateCredential cred = new GateCredential();
        cred.setQrCode("QR-" + uuid());
        cred.setVoyageId(voyage.getId());
        cred.setRevisionId(rev.getId());
        cred.setStatus(Types.GateCredentialStatus.ACTIVE);
        credentialRepo.save(cred);
        recordEvent(voyage, Types.VoyageEventType.ROSTER_REVISION,
                "名册 " + rev.getRevisionNo() + " 三方确认齐备，放行并签发二维码 " + cred.getQrCode(), "SYSTEM");
        return true;
    }

    // ---------------- 离港窗 ----------------

    @Transactional
    public DepartureWindow registerWindow(String voyageNo, String portCode, String kind,
                                          LocalDateTime from, LocalDateTime to) {
        Voyage voyage = requireVoyage(voyageNo);
        if (to == null || from == null || !to.isAfter(from)) {
            throw BizException.badRequest("离港窗时间不合法");
        }
        DepartureWindow w = new DepartureWindow();
        w.setWindowNo("WIN-" + uuid());
        w.setVoyageId(voyage.getId());
        w.setPortCode(portCode);
        w.setWindowKind(kind);
        w.setWindowFrom(from);
        w.setWindowTo(to);
        return windowRepo.save(w);
    }

    // ---------------- 闸口扫码 ----------------

    /**
     * 闸口只接受当时有效的船员版本。
     * 已离港后的迟到签到只留作核查（RECORDED_FOR_AUDIT），不回滚新名册。
     */
    @Transactional
    public GateScanResult scan(String qrCode) {
        LocalDateTime at = now();
        GateCredential cred = credentialRepo.lockByQrCode(qrCode).orElse(null);
        if (cred == null) {
            return logAndReturnReject(null, qrCode, "UNKNOWN_QR", "二维码无对应凭证", at, false, null, null);
        }
        Voyage voyage = voyageRepo.findById(cred.getVoyageId()).orElseThrow();
        boolean afterDeparture = voyage.getState() != Types.VoyageState.APPROVED;
        CrewRosterRevision current = revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId())
                .orElseThrow();

        if (afterDeparture) {
            // 离港后迟到签到：仅留核查，绝不回滚新名册
            GateScanLog log = logScan(voyage.getId(), qrCode, Types.GateScanVerdict.REJECT,
                    "LATE_CHECKIN_RECORDED", "船舶已离港，迟到签到仅留作核查，不回滚名册 "
                            + current.getRevisionNo(), true, Types.LateCheckinStatus.RECORDED_FOR_AUDIT, at);
            recordEvent(voyage, Types.VoyageEventType.GATE_SCAN,
                    "离港后迟到扫码留痕（不回滚名册）：" + qrCode, "GATE");
            return new GateScanResult(log, Types.GateScanVerdict.REJECT, "LATE_CHECKIN_RECORDED",
                    log.getDetail(), true, current.getRevisionNo(), List.of());
        }

        if (cred.getStatus() == Types.GateCredentialStatus.REVOKED) {
            return logAndReturnReject(voyage.getId(), qrCode, "OLD_CREDENTIAL_REVOKED",
                    "旧二维码已随名册重签撤销：" + cred.getRevokeReason(), at, false, current, null);
        }
        if (!cred.getRevisionId().equals(current.getId()) || current.getStatus() == Types.RosterStatus.SUPERSEDED) {
            return logAndReturnReject(voyage.getId(), qrCode, "NOT_CURRENT_VERSION",
                    "凭证不属于当前有效名册版本（当前 " + current.getRevisionNo() + "）",
                    at, false, current, null);
        }
        List<AckView> acks = toViews(ackRepo.findByRevisionIdOrderByPartyAsc(current.getId()));
        List<Types.RosterParty> pending = pendingParties(acks);
        if (current.getStatus() != Types.RosterStatus.RELEASED || !pending.isEmpty()) {
            return logAndReturnReject(voyage.getId(), qrCode, "ACK_PENDING",
                    "关键接收方未确认，待放行：" + pending, at, false, current, pending);
        }
        Optional<DepartureWindow> currentWindow = windowRepo.findByVoyageIdOrderByWindowFromAsc(voyage.getId())
                .stream().filter(w -> "CURRENT".equals(w.getWindowKind())).findFirst();
        if (currentWindow.isEmpty() || !currentWindow.get().availableAt(at)) {
            String to = currentWindow.map(w -> w.getWindowTo().toString()).orElse("无登记窗口");
            return logAndReturnReject(voyage.getId(), qrCode, "WINDOW_CLOSED",
                    "当前离港窗已关闭（窗至 " + to + "），11:00 后原窗口不再可用", at, false, current, null);
        }

        // 离港闸按整份名册重新核验岗位覆盖（含休息、证书、船型、专项资质），而非只查被替换的一行
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        List<CrewMember> members = memberRepo.findByRevisionId(current.getId());
        List<String> issues = validateWholeRoster(members, vessel, splitCsv(current.getRequiredQualifications()), at, true);
        if (!issues.isEmpty()) {
            String reason = issues.stream().anyMatch(i -> i.contains("休息")) ? "NOT_YET_RESTED" : "COVERAGE_FAIL";
            return logAndReturnReject(voyage.getId(), qrCode, reason,
                    "整份名册核验未通过：" + String.join("；", issues), at, false, current, null);
        }

        GateScanLog log = logScan(voyage.getId(), qrCode, Types.GateScanVerdict.PASS, null,
                "名册 " + current.getRevisionNo() + " 有效，窗口内放行", false, null, at);
        recordEvent(voyage, Types.VoyageEventType.GATE_SCAN, "闸口扫码放行：" + qrCode, "GATE");
        return new GateScanResult(log, Types.GateScanVerdict.PASS, null, log.getDetail(), false,
                current.getRevisionNo(), List.of());
    }

    // ---------------- 投影协议 ----------------

    public record Projection(String version, String hash, List<String> fields) {
    }

    /** 按接收方协议计算字段投影：应急电话仅应急方包含；船长/岗位/航次身份变化会同时改变三方哈希 */
    Projection project(Types.RosterParty party, CrewRosterRevision rev, List<CrewMember> members,
                       Voyage voyage, Vessel vessel) {
        List<String> fields = PROJECTION_FIELDS.get(party);
        StringBuilder canonical = new StringBuilder();
        canonical.append("proto=").append(PROTOCOL_VERSION).append('|')
                .append("voyage=").append(voyage.getVoyageNo()).append('|')
                .append("vessel=").append(vessel.getVesselNo()).append(':').append(vessel.getVesselType());
        List<CrewMember> ordered = members.stream().sorted(Comparator.comparing(CrewMember::getId)).toList();
        for (CrewMember m : ordered) {
            canonical.append('|').append(projectMember(party, m));
        }
        String hash = sha256(canonical.toString());
        return new Projection(PROTOCOL_VERSION, hash, fields);
    }

    private String projectMember(Types.RosterParty party, CrewMember m) {
        StringBuilder b = new StringBuilder(m.getPersonNo());
        List<String> tail = switch (party) {
            case PORT -> List.of(m.getName(), m.getPositionCode(), m.getVoyageDuty(), m.getCertificateNo(),
                    m.getCertValidTo().toString(), m.getVesselTypeGrants(), nz(m.getQualificationCodes()));
            case INSURER -> List.of(m.getName(), m.getPositionCode(), m.getVoyageDuty(), m.getCertificateNo(),
                    nz(m.getQualificationCodes()));
            case EMERGENCY -> List.of(m.getName(), m.getPositionCode(), m.getVoyageDuty(), nz(m.getEmergencyPhone()));
        };
        for (String v : tail) {
            b.append(':').append(v);
        }
        return b.toString();
    }

    private List<AckView> createAcks(CrewRosterRevision rev, Vessel vessel, Voyage voyage,
                                     CrewRosterRevision previous) {
        List<CrewMember> members = memberRepo.findByRevisionId(rev.getId());
        List<AckView> views = new ArrayList<>();
        for (Types.RosterParty party : PARTIES) {
            Projection p = project(party, rev, members, voyage, vessel);
            RosterAcknowledgement ack = new RosterAcknowledgement();
            ack.setAckNo("ACK-" + uuid());
            ack.setRevisionId(rev.getId());
            ack.setVoyageId(voyage.getId());
            ack.setParty(party);
            ack.setProjectionVersion(p.version());
            ack.setProjectionHash(p.hash());
            ack.setProjectionFields(String.join(",", p.fields()));
            RosterAcknowledgement old = previous == null ? null
                    : ackRepo.findByRevisionIdAndParty(previous.getId(), party).orElse(null);
            // 只有投影内容与协议版本逐项一致时旧确认才可沿用；修订号不同永不直接放行
            if (old != null && old.getStatus() == Types.AcknowledgementStatus.CONFIRMED
                    && old.getProjectionVersion().equals(p.version())
                    && old.getProjectionHash().equals(p.hash())) {
                ack.setStatus(Types.AcknowledgementStatus.CONFIRMED);
                ack.setCarriedFromAckId(old.getId());
                ack.setConfirmedBy(old.getConfirmedBy());
                ack.setConfirmedAt(now());
            } else {
                ack.setStatus(Types.AcknowledgementStatus.PENDING);
                ack.setInvalidatedReason(invalidationReason(old, p, rev.getChangeKind()));
            }
            ackRepo.save(ack);
            views.add(toView(ack));
        }
        return views;
    }

    private String invalidationReason(RosterAcknowledgement old, Projection p, Types.RosterChangeKind kind) {
        if (old == null) {
            return "初版名册，待签认";
        }
        if (!old.getProjectionVersion().equals(p.version())) {
            return "投影协议版本由 " + old.getProjectionVersion() + " 变为 " + p.version() + "，旧确认失效";
        }
        return switch (kind) {
            case SUBSTITUTE -> "船长/岗位/航次职责相关投影字段变化，哈希不一致，旧确认失效";
            case CORRECTION -> "投影字段内容更正，哈希不一致，旧确认失效";
            case REST -> "重签后投影字段变化，哈希不一致，旧确认失效";
            default -> "投影内容哈希不一致，旧确认失效";
        };
    }

    // ---------------- 核验 ----------------

    /** 替换申请五项核验：岗位、船型权限、证书、休息、航次职责 */
    private List<String> validateReplacement(CrewRosterRevision current, CrewPerson p, Vessel vessel,
                                             LocalDateTime at) {
        List<CrewMember> currentMembers = memberRepo.findByRevisionId(current.getId());
        List<String> failures = new ArrayList<>();
        boolean positionExists = currentMembers.stream()
                .anyMatch(m -> m.getPositionCode().equals(p.getPositionCode()));
        if (!positionExists) {
            failures.add("岗位核验失败：名册无 " + p.getPositionCode() + " 岗位，替补无法对位");
        }
        if (!splitCsv(p.getVesselTypeGrants()).contains(vessel.getVesselType())) {
            failures.add("船型权限核验失败：" + p.getName() + " 无 " + vessel.getVesselType() + " 船型权限");
        }
        if (!p.getCertValidTo().isAfter(at)) {
            failures.add("证书核验失败：" + p.getName() + " 证书 " + p.getCertificateNo() + " 已过期/即将失效");
        }
        if (p.getRestReadyAt().isAfter(at)) {
            failures.add("休息核验失败：" + p.getName() + " 休息达标时刻 " + p.getRestReadyAt() + " 晚于当前 " + at);
        }
        // 航次职责：替补须按同岗位对位接替，岗位职责（当班船长/值班轮机员…）随岗位确定，
        // 替换后整份名册的职责唯一性由 validateWholeRoster 统一重核
        if (positionExists && currentMembers.stream()
                .noneMatch(m -> m.getPositionCode().equals(p.getPositionCode())
                        && m.getVoyageDuty().equals(dutyOf(p)))) {
            failures.add("航次职责核验失败：替补 " + p.getName() + " 的航次职责 " + dutyOf(p) + " 无法对位接替");
        }
        return failures;
    }

    /**
     * 整份名册岗位覆盖重核：船长唯一、人员不重复、证书/船型逐人达标、专项资质有人覆盖；
     * checkRest=true 时再逐人核休息（离港闸与重签），初版建档不核休息（建档时未必已休满）。
     */
    private List<String> validateWholeRoster(List<? extends Object> rows, Vessel vessel,
                                             List<String> requiredQualifications, LocalDateTime at,
                                             boolean checkRest) {
        List<String> failures = new ArrayList<>();
        List<CrewMember> members = new ArrayList<>();
        List<CrewPerson> persons = new ArrayList<>();
        for (Object o : rows) {
            if (o instanceof CrewMember m) {
                members.add(m);
            } else if (o instanceof CrewPerson p) {
                persons.add(p);
            }
        }
        boolean snapshot = !members.isEmpty();
        int captainCount = 0;
        Set<String> seen = new HashSet<>();
        Set<String> coveredQuals = new HashSet<>();
        int size = snapshot ? members.size() : persons.size();
        for (int i = 0; i < size; i++) {
            String personNo = snapshot ? members.get(i).getPersonNo() : persons.get(i).getPersonNo();
            String name = snapshot ? members.get(i).getName() : persons.get(i).getName();
            String position = snapshot ? members.get(i).getPositionCode() : persons.get(i).getPositionCode();
            String certNo = snapshot ? members.get(i).getCertificateNo() : persons.get(i).getCertificateNo();
            LocalDateTime certTo = snapshot ? members.get(i).getCertValidTo() : persons.get(i).getCertValidTo();
            String grants = snapshot ? members.get(i).getVesselTypeGrants() : persons.get(i).getVesselTypeGrants();
            String quals = snapshot ? members.get(i).getQualificationCodes() : persons.get(i).getQualificationCodes();
            LocalDateTime restReady = snapshot ? members.get(i).getRestReadyAt() : persons.get(i).getRestReadyAt();

            if ("CAPTAIN".equals(position)) {
                captainCount++;
            }
            if (!seen.add(personNo)) {
                failures.add("人员 " + name + "（" + personNo + "）在名册中重复");
            }
            if (!certTo.isAfter(at)) {
                failures.add("证书核验失败：" + name + " 证书 " + certNo + " 不在有效期");
            }
            if (!splitCsv(grants).contains(vessel.getVesselType())) {
                failures.add("船型权限核验失败：" + name + " 无 " + vessel.getVesselType() + " 船型权限");
            }
            if (checkRest && restReady.isAfter(at)) {
                failures.add("休息核验失败：" + name + " 需休息至 " + restReady + " 才满足离岗条件");
            }
            if (quals != null) {
                coveredQuals.addAll(splitCsv(quals));
            }
        }
        if (captainCount != 1) {
            failures.add("岗位覆盖失败：当班船长须恰好 1 名，实际 " + captainCount + " 名");
        }
        for (String q : requiredQualifications) {
            if (!coveredQuals.contains(q)) {
                failures.add("专项资质覆盖失败：整份名册无人持有 " + q + " 资质（如唯一持冷藏设备资格者请假）");
            }
        }
        return failures;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public RosterStatusView statusOf(String voyageNo) {
        Voyage voyage = requireVoyage(voyageNo);
        CrewRosterRevision current = revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId())
                .orElseThrow(() -> BizException.notFound("航次无名册: " + voyageNo));
        List<CrewMember> members = memberRepo.findByRevisionId(current.getId());
        List<AckView> acks = toViews(ackRepo.findByRevisionIdOrderByPartyAsc(current.getId()));
        List<Types.RosterParty> pending = pendingParties(acks);
        GateCredential active = credentialRepo
                .findByVoyageIdAndStatus(voyage.getId(), Types.GateCredentialStatus.ACTIVE).stream()
                .filter(c -> c.getRevisionId().equals(current.getId())).findFirst().orElse(null);
        Vessel vessel = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        List<String> issues = validateWholeRoster(members, vessel, splitCsv(current.getRequiredQualifications()), now(), true);
        List<WindowView> windows = windowRepo.findByVoyageIdOrderByWindowFromAsc(voyage.getId()).stream()
                .map(w -> new WindowView(w, w.availableAt(now()))).toList();
        return new RosterStatusView(current, members, acks, pending.isEmpty(), pending, active, windows, issues);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<CrewRosterRevision> historyOf(String voyageNo) {
        Voyage voyage = requireVoyage(voyageNo);
        return revisionRepo.findByVoyageIdOrderByRevisionSeqAsc(voyage.getId());
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<OwnerDecision> decisionsOf(String voyageNo) {
        Voyage voyage = requireVoyage(voyageNo);
        return decisionRepo.findByVoyageIdOrderByDecidedAtAsc(voyage.getId());
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<GateScanLog> scansOf(String voyageNo) {
        Voyage voyage = requireVoyage(voyageNo);
        return scanRepo.findByVoyageIdOrderByScannedAtAsc(voyage.getId());
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<AckView> acksOf(Long revisionId) {
        return toViews(ackRepo.findByRevisionIdOrderByPartyAsc(revisionId));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<AckView> acksOfRevision(String revisionNo) {
        CrewRosterRevision rev = revisionRepo.findByRevisionNo(revisionNo)
                .orElseThrow(() -> BizException.notFound("名册修订不存在: " + revisionNo));
        return toViews(ackRepo.findByRevisionIdOrderByPartyAsc(rev.getId()));
    }

    /** 工作台：待放行修订与责任接收方 */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Map<String, Object>> pendingReleaseTasks() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (CrewRosterRevision rev : revisionRepo.findAll()) {
            if (rev.getStatus() == Types.RosterStatus.RELEASED || rev.getStatus() == Types.RosterStatus.SUPERSEDED) {
                continue;
            }
            List<Types.RosterParty> pending = pendingParties(
                    toViews(ackRepo.findByRevisionIdOrderByPartyAsc(rev.getId())));
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("revisionNo", rev.getRevisionNo());
            task.put("voyageId", rev.getVoyageId());
            task.put("status", rev.getStatus());
            task.put("pendingParties", pending);
            task.put("responsibility", "待放行：关键接收方 " + pending + " 未确认，责任在对应接收方补签，船东负责催办");
            task.put("nextAction", "POST /api/crew/revisions/" + rev.getRevisionNo() + "/ack?party=...");
            tasks.add(task);
        }
        return tasks;
    }

    // ---------------- 内部工具 ----------------

    private CrewRosterRevision newRevision(Voyage voyage, int seq, Long supersedesId,
                                           Types.RosterChangeKind kind, String reason,
                                           List<String> requiredQualifications) {
        CrewRosterRevision rev = new CrewRosterRevision();
        rev.setRevisionNo("RST-" + uuid());
        rev.setVoyageId(voyage.getId());
        rev.setRevisionSeq(seq);
        rev.setSupersedesId(supersedesId);
        rev.setChangeKind(kind);
        rev.setChangeReason(reason);
        rev.setRequiredQualifications(requiredQualifications == null ? null : String.join(",", requiredQualifications));
        rev.setStatus(Types.RosterStatus.PENDING_RELEASE);
        return rev;
    }

    private void attachSnapshots(CrewRosterRevision rev, List<CrewPerson> persons,
                                 Map<String, String> phoneOverrides) {
        revisionRepo.save(rev);
        for (CrewPerson p : persons) {
            CrewMember m = new CrewMember();
            m.setRevisionId(rev.getId());
            m.setPersonNo(p.getPersonNo());
            m.setName(p.getName());
            m.setPositionCode(p.getPositionCode());
            m.setVoyageDuty(dutyOf(p));
            m.setCertificateNo(p.getCertificateNo());
            m.setCertValidTo(p.getCertValidTo());
            m.setVesselTypeGrants(p.getVesselTypeGrants());
            m.setQualificationCodes(p.getQualificationCodes());
            m.setRestReadyAt(p.getRestReadyAt());
            m.setEmergencyPhone(phoneOverrides.getOrDefault(p.getPersonNo(), p.getEmergencyPhone()));
            memberRepo.save(m);
        }
    }

    private OwnerDecision saveDecision(Voyage voyage, Types.OwnerDecisionType type, String reason, String actor) {
        OwnerDecision d = new OwnerDecision();
        d.setDecisionNo("DEC-" + uuid());
        d.setVoyageId(voyage.getId());
        d.setDecisionType(type);
        d.setDecidedBy(actor);
        d.setDecidedAt(now());
        d.setReason(reason);
        // 通知回执：模拟通知渠道回执号，与船东决定成对保留
        d.setNotifyReceiptNo("NTF-" + uuid());
        d.setNotifiedAt(now());
        return decisionRepo.save(d);
    }

    private List<String> revokeActiveCredentials(Long voyageId, String reason) {
        List<String> revoked = new ArrayList<>();
        for (GateCredential c : credentialRepo.findByVoyageIdAndStatus(voyageId,
                Types.GateCredentialStatus.ACTIVE)) {
            c.setStatus(Types.GateCredentialStatus.REVOKED);
            c.setRevokedAt(now());
            c.setRevokeReason(reason);
            credentialRepo.save(c);
            revoked.add(c.getQrCode());
        }
        return revoked;
    }

    private GateScanLog logScan(Long voyageId, String qr, Types.GateScanVerdict verdict, String reasonCode,
                                String detail, boolean afterDeparture, Types.LateCheckinStatus audit,
                                LocalDateTime at) {
        GateScanLog log = new GateScanLog();
        log.setScanNo("SCN-" + uuid());
        log.setVoyageId(voyageId);
        log.setQrCode(qr);
        log.setVerdict(verdict);
        log.setReasonCode(reasonCode);
        log.setDetail(detail);
        log.setAfterDeparture(afterDeparture);
        log.setAuditStatus(audit);
        log.setScannedAt(at);
        return scanRepo.save(log);
    }

    private GateScanResult logAndReturnReject(Long voyageId, String qr, String reason, String detail,
                                              LocalDateTime at, boolean afterDeparture,
                                              CrewRosterRevision current, List<Types.RosterParty> pending) {
        GateScanLog log = logScan(voyageId, qr, Types.GateScanVerdict.REJECT, reason, detail,
                afterDeparture, afterDeparture ? Types.LateCheckinStatus.RECORDED_FOR_AUDIT : null, at);
        return new GateScanResult(log, Types.GateScanVerdict.REJECT, reason, detail, afterDeparture,
                current == null ? null : current.getRevisionNo(), pending == null ? List.of() : pending);
    }

    private List<AckView> toViews(List<RosterAcknowledgement> acks) {
        return acks.stream().map(this::toView).toList();
    }

    private AckView toView(RosterAcknowledgement a) {
        return new AckView(a.getId(), a.getAckNo(), a.getParty(), a.getStatus(), a.getProjectionVersion(),
                a.getProjectionHash(), splitCsv(a.getProjectionFields()), a.getCarriedFromAckId(),
                a.getConfirmedBy(), a.getConfirmedAt(), a.getInvalidatedReason());
    }

    private CrewRosterRevision lockCurrent(Voyage voyage) {
        revisionRepo.lockByVoyageId(voyage.getId());
        return revisionRepo.findTopByVoyageIdOrderByRevisionSeqDesc(voyage.getId())
                .orElseThrow(() -> BizException.notFound("航次无名册: " + voyage.getVoyageNo()));
    }

    private Voyage requireVoyage(String voyageNo) {
        return voyageRepo.findByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
    }

    private List<CrewPerson> loadPersons(List<String> personNos) {
        if (personNos == null || personNos.isEmpty()) {
            throw BizException.badRequest("名册至少包含一名船员");
        }
        List<CrewPerson> persons = new ArrayList<>();
        for (String no : personNos) {
            CrewPerson p = personRepo.findByPersonNo(no)
                    .orElseThrow(() -> BizException.notFound("人员不存在: " + no));
            if (!p.isActive()) {
                throw BizException.conflict("PERSON_ON_LEAVE",
                        "人员 " + p.getName() + "（" + no + "）已请假，不能进入名册");
            }
            persons.add(p);
        }
        return persons;
    }

    /** 从上一版名册快照行重建人员对象，保留更正过的应急电话等快照值，不回退到主档 */
    private List<CrewPerson> snapshotPersonsOf(CrewRosterRevision rev) {
        List<CrewPerson> persons = new ArrayList<>();
        for (CrewMember m : memberRepo.findByRevisionId(rev.getId())) {
            CrewPerson p = new CrewPerson();
            p.setPersonNo(m.getPersonNo());
            p.setName(m.getName());
            p.setPositionCode(m.getPositionCode());
            p.setCertificateNo(m.getCertificateNo());
            p.setCertValidTo(m.getCertValidTo());
            p.setVesselTypeGrants(m.getVesselTypeGrants());
            p.setQualificationCodes(m.getQualificationCodes());
            p.setRestReadyAt(m.getRestReadyAt());
            p.setEmergencyPhone(m.getEmergencyPhone());
            persons.add(p);
        }
        return persons;
    }

    private CrewPerson masterOf(CrewRosterRevision rev, String position) {
        String personNo = memberRepo.findByRevisionId(rev.getId()).stream()
                .filter(m -> m.getPositionCode().equals(position)).map(CrewMember::getPersonNo)
                .findFirst().orElseThrow(() -> BizException.unprocessable("POSITION_GAP", "名册无 " + position + " 岗位"));
        return personRepo.findByPersonNo(personNo).orElseThrow();
    }

    private void recordEvent(Voyage voyage, Types.VoyageEventType type, String detail, String actor) {
        VoyageEvent e = new VoyageEvent();
        e.setVoyageId(voyage.getId());
        e.setPermitId(voyage.getPermitId());
        e.setEventType(type);
        e.setDetail(detail);
        e.setActor(actor);
        eventRepo.save(e);
    }

    private int countPending(List<AckView> acks) {
        return (int) acks.stream().filter(a -> a.status() == Types.AcknowledgementStatus.PENDING).count();
    }

    private int countCarried(List<AckView> acks) {
        return (int) acks.stream().filter(a -> a.carriedFromAckId() != null).count();
    }

    private List<Types.RosterParty> pendingParties(List<AckView> acks) {
        return acks.stream().filter(a -> a.status() == Types.AcknowledgementStatus.PENDING)
                .map(AckView::party).toList();
    }

    private String dutyOf(CrewPerson p) {
        return switch (p.getPositionCode()) {
            case "CAPTAIN" -> "当班船长";
            case "CHIEF_ENGINEER" -> "值班轮机长";
            case "ENGINEER" -> "值班轮机员";
            case "SAILOR" -> "值班船员";
            default -> p.getPositionCode();
        };
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
