package com.fishport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fishport.service.SimClock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N-5 船员名册替换验收：
 * 1. 原船长按时满足休息，赶当前窗最后时点；11:00 后原窗口不再可用；
 * 2. 替补迟到超过窗口、替换后旧二维码再刷被拒；
 * 3. 保险回执失败 → 待放行、离港阻断、责任在保险方；
 * 4. 离港后迟到签到只留核查、不回滚新名册；
 * 5. 投影模型：船长/岗位/航次变化三方失效（待重签 3）；仅改应急电话只失效应急方（待重签 1）；
 * 6. 旧投影确认仅在投影内容与协议版本逐项一致时沿用；严禁新名册配旧确认；
 * 7. 离港闸按整份名册重核岗位覆盖（唯一 REEFER 轮机员请假 → 覆盖失败）。
 */
class CrewRosterTest extends BaseIntegrationTest {

    private static final String D = "2026-11-26";
    private static final List<String> BASE_CREW = List.of("P-CAP-01", "P-ENG-01", "P-SAIL-01");

    @Autowired
    private SimClock simClock;

    @AfterEach
    void resetClock() {
        simClock.reset();
    }

    // ---------------- 场景一：原船长休息达标，赶当前窗最后时点 ----------------

    @Test
    void original_captain_rest_on_time_passes_at_last_window_point() {
        String voyageNo = setupVoyage();
        fixClock(D + "T09:00:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        String rev1 = currentRevisionNo(voyageNo);
        ackAll(rev1);
        String qr1 = activeQr(voyageNo);

        // 09:00 原船长还差两小时休息：闸口拒绝
        Map<String, Object> scanEarly = scan(qr1);
        assertThat(scanEarly.get("verdict")).isEqualTo("REJECT");
        assertThat(scanEarly.get("reasonCode")).isEqualTo("NOT_YET_RESTED");

        // 09:00 不能按“休息达标”重签
        ResponseEntity<Map> keepEarly = as(OWNER, OWNER_PW).postForEntity(
                "/api/crew/voyages/" + voyageNo + "/keep-captain-rest",
                Map.of("reason", "原船长继续"), Map.class);
        assertThat(keepEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 11:00 休息达标：重签，人员数据未变 → 三方投影逐项一致，全部沿用（待重签 0、沿用 3）
        fixClock(D + "T11:00:00");
        Map<String, Object> keep = post(OWNER, OWNER_PW,
                "/api/crew/voyages/" + voyageNo + "/keep-captain-rest",
                Map.of("reason", "原船长休息满两小时后继续"));
        assertThat(keep.get("reSignRequired")).isEqualTo(0);
        assertThat(keep.get("carried")).isEqualTo(3);
        assertThat(keep.get("releasable")).isEqualTo(true);
        String rev2 = (String) ((Map<String, Object>) keep.get("revision")).get("revisionNo");
        assertThat(keep.get("revokedQrCodes")).isEqualTo(List.of(qr1));

        // 11:00 是当前窗最后时点：新二维码放行
        String qr2 = activeQr(voyageNo);
        Map<String, Object> scanOnTime = scan(qr2);
        assertThat(scanOnTime.get("verdict")).isEqualTo("PASS");
        assertThat(scanOnTime.get("effectiveRevisionNo")).isEqualTo(rev2);

        // 11:00 后原窗口不得再显示可用，扫码拒绝
        fixClock(D + "T11:01:00");
        Map<String, Object> status = rosterStatus(voyageNo);
        List<Map<String, Object>> windows = (List<Map<String, Object>>) status.get("windows");
        Map<String, Object> currentWindow = windows.stream()
                .filter(w -> "CURRENT".equals(((Map<String, Object>) w.get("window")).get("windowKind")))
                .findFirst().orElseThrow();
        assertThat(currentWindow.get("available")).isEqualTo(false);
        Map<String, Object> scanLate = scan(qr2);
        assertThat(scanLate.get("verdict")).isEqualTo("REJECT");
        assertThat(scanLate.get("reasonCode")).isEqualTo("WINDOW_CLOSED");

        // 原船长休息方案同样保留船东决定与通知回执
        List<Map<String, Object>> decisions = as(OWNER, OWNER_PW)
                .getForEntity("/api/crew/voyages/" + voyageNo + "/decisions", List.class).getBody();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("decisionType")).isEqualTo("KEEP_CAPTAIN_REST");
        assertThat(decisions.get(0).get("notifyReceiptNo")).asString().startsWith("NTF-");
    }

    // ---------------- 场景二：替补迟到超过窗口 + 旧二维码再刷 + 待放行责任 ----------------

    @Test
    void substitute_late_beyond_window_old_qr_rejected_and_pending_release() {
        String voyageNo = setupVoyage();
        fixClock(D + "T09:00:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        ackAll(currentRevisionNo(voyageNo));
        String qr1 = activeQr(voyageNo);

        // 09:00 替补船长 09:15 才休息达标：替换申请被拒（休息核验）
        ResponseEntity<Map> early = as(OWNER, OWNER_PW).postForEntity(
                "/api/crew/voyages/" + voyageNo + "/substitute",
                Map.of("replacementPersonNo", "P-CAP-02", "reason", "启用替补"), Map.class);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 11:30 替补完成重签（已晚于当前窗 11:00）：船长/岗位/航次身份变化 → 三方确认全部失效，待重签 3
        fixClock(D + "T11:30:00");
        Map<String, Object> sub = post(OWNER, OWNER_PW,
                "/api/crew/voyages/" + voyageNo + "/substitute",
                Map.of("replacementPersonNo", "P-CAP-02", "reason", "启用替补赶另一港窗口"));
        assertThat(sub.get("reSignRequired")).isEqualTo(3);
        assertThat(sub.get("carried")).isEqualTo(0);
        assertThat(sub.get("releasable")).isEqualTo(false);
        assertThat(sub.get("pendingParties")).isEqualTo(List.of("PORT", "INSURER", "EMERGENCY"));
        String rev2 = (String) ((Map<String, Object>) sub.get("revision")).get("revisionNo");
        // 重签即撤销旧二维码
        assertThat(sub.get("revokedQrCodes")).isEqualTo(List.of(qr1));

        // 旧二维码再刷：拒绝
        Map<String, Object> oldScan = scan(qr1);
        assertThat(oldScan.get("verdict")).isEqualTo("REJECT");
        assertThat(oldScan.get("reasonCode")).isEqualTo("OLD_CREDENTIAL_REVOKED");

        // 只签两方：仍待放行，页面显示待放行及责任人
        ack(rev2, "PORT");
        ack(rev2, "INSURER");
        Map<String, Object> pending = rosterStatus(voyageNo);
        assertThat(pending.get("releasable")).isEqualTo(false);
        assertThat(pending.get("pendingParties")).isEqualTo(List.of("EMERGENCY"));
        Map<String, Object> scanPending = scan(qr1);
        assertThat(scanPending.get("reasonCode")).isEqualTo("OLD_CREDENTIAL_REVOKED");

        // 工作台有待放行任务且点名应急方
        List<Map<String, Object>> tasks = as(OWNER, OWNER_PW)
                .getForEntity("/api/workbench/tasks", List.class).getBody();
        boolean hasPendingTask = tasks.stream().anyMatch(t -> "名册待放行".equals(t.get("category"))
                && String.valueOf(t.get("responsibility")).contains("EMERGENCY"));
        assertThat(hasPendingTask).isTrue();

        // 补齐应急方 → 放行并签发新二维码；当前窗已关，另一港窗口仍可用
        ack(rev2, "EMERGENCY");
        Map<String, Object> released = rosterStatus(voyageNo);
        assertThat(released.get("releasable")).isEqualTo(true);
        assertThat(released.get("activeCredential")).isNotNull();
        Map<String, Object> scanNow = scan(activeQr(voyageNo));
        assertThat(scanNow.get("reasonCode")).isEqualTo("WINDOW_CLOSED");
        List<Map<String, Object>> windows = (List<Map<String, Object>>) released.get("windows");
        Map<String, Object> alt = windows.stream()
                .filter(w -> "ALTERNATIVE".equals(((Map<String, Object>) w.get("window")).get("windowKind")))
                .findFirst().orElseThrow();
        assertThat(alt.get("available")).isEqualTo(true);

        // 船东决定与通知回执均保留
        List<Map<String, Object>> decisions = as(OWNER, OWNER_PW)
                .getForEntity("/api/crew/voyages/" + voyageNo + "/decisions", List.class).getBody();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("decisionType")).isEqualTo("USE_SUBSTITUTE");
        assertThat(decisions.get(0).get("notifyReceiptNo")).asString().startsWith("NTF-");
    }

    // ---------------- 场景三：保险回执失败阻断离港；离港后迟到签到不回滚 ----------------

    @Test
    void insurance_ack_failure_blocks_departure_and_late_checkin_kept_for_audit() {
        String voyageNo = setupVoyage();
        fixClock(D + "T09:30:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        ackAll(currentRevisionNo(voyageNo));

        // 替补重签后，保险回执失败（只签港口与应急）
        Map<String, Object> sub = post(OWNER, OWNER_PW,
                "/api/crew/voyages/" + voyageNo + "/substitute",
                Map.of("replacementPersonNo", "P-CAP-02", "reason", "启用替补"));
        String rev2 = (String) ((Map<String, Object>) sub.get("revision")).get("revisionNo");
        ack(rev2, "PORT");
        ack(rev2, "EMERGENCY");

        // 港口预约仍可登记（不因名册待放行而消失），但离港被阻断
        ResponseEntity<Map> book = as(DISPATCHER, DISPATCHER_PW).postForEntity("/api/appointments", Map.of(
                "voyageNo", voyageNo, "portCode", "PORT1", "mode", "FIXED",
                "requestedStart", D + "T10:00:00", "durationMin", 40,
                "latestFinish", D + "T11:00:00", "expectedUnloadKg", 100), Map.class);
        assertThat(book.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> departBlocked = rest.postForEntity(
                "/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "DEPART", "eventId", "E-BLOCK"), Map.class);
        assertThat(departBlocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(departBlocked.getBody().get("code")).isEqualTo("ROSTER_NOT_RELEASED");

        // 保险方补签 → 放行 → 离港成功（船舶许可恢复可用）
        ack(rev2, "INSURER");
        Map<String, Object> depart = rest.postForEntity(
                "/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "DEPART", "eventId", "E-OK"), Map.class).getBody();
        assertThat(((Map<String, Object>) depart.get("payload")).get("state")).isEqualTo("DEPARTED");

        // 离港后迟到签到：只留核查，不回滚新名册
        String qr = activeQr(voyageNo);
        Map<String, Object> late = scan(qr);
        assertThat(late.get("verdict")).isEqualTo("REJECT");
        assertThat(late.get("reasonCode")).isEqualTo("LATE_CHECKIN_RECORDED");
        assertThat(late.get("afterDeparture")).isEqualTo(true);

        List<Map<String, Object>> scans = as(OWNER, OWNER_PW)
                .getForEntity("/api/crew/voyages/" + voyageNo + "/scans", List.class).getBody();
        Map<String, Object> audit = scans.get(scans.size() - 1);
        assertThat(audit.get("auditStatus")).isEqualTo("RECORDED_FOR_AUDIT");
        // 新名册未被回滚：当前版本仍是 rev2 且保持 RELEASED
        Map<String, Object> status = rosterStatus(voyageNo);
        assertThat(((Map<String, Object>) status.get("current")).get("revisionNo")).isEqualTo(rev2);
        assertThat(((Map<String, Object>) status.get("current")).get("status")).isEqualTo("RELEASED");
    }

    // ---------------- 场景四：投影模型——仅改应急电话只失效应急方（待重签 1） ----------------

    @Test
    void phone_correction_invalidates_only_emergency_projection() {
        String voyageNo = setupVoyage();
        fixClock(D + "T09:30:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        String rev1 = currentRevisionNo(voyageNo);
        ackAll(rev1);
        String qr1 = activeQr(voyageNo);

        Map<String, Object> rev1Acks = acks(rev1);
        String portHashRev1 = hashOf(rev1Acks, "PORT");
        String insurerHashRev1 = hashOf(rev1Acks, "INSURER");
        String emergencyHashRev1 = hashOf(rev1Acks, "EMERGENCY");

        // 仅更正应急联系人电话：整单模型将待重签 3 份；本投影模型只待 1 份
        Map<String, Object> correction = post(OWNER, OWNER_PW,
                "/api/crew/voyages/" + voyageNo + "/corrections",
                Map.of("emergencyPhones", Map.of("P-SAIL-01", "13711112222"),
                        "reason", "更正船员陈应急电话"));
        assertThat(correction.get("reSignRequired")).isEqualTo(1);
        assertThat(correction.get("carried")).isEqualTo(2);
        String rev2 = (String) ((Map<String, Object>) correction.get("revision")).get("revisionNo");

        List<Map<String, Object>> rev2Acks = (List<Map<String, Object>>) correction.get("acknowledgements");
        Map<String, Object> portAck = ackOf(rev2Acks, "PORT");
        Map<String, Object> insurerAck = ackOf(rev2Acks, "INSURER");
        Map<String, Object> emergencyAck = ackOf(rev2Acks, "EMERGENCY");
        // 港口/保险投影不含应急电话：哈希逐项一致 → 旧确认沿用
        assertThat(portAck.get("status")).isEqualTo("CONFIRMED");
        assertThat(portAck.get("carriedFromAckId")).isNotNull();
        assertThat(portAck.get("projectionHash")).isEqualTo(portHashRev1);
        assertThat(insurerAck.get("projectionHash")).isEqualTo(insurerHashRev1);
        // 应急投影含电话：哈希变化 → 失效待重签，且记录失效原因
        assertThat(emergencyAck.get("status")).isEqualTo("PENDING");
        assertThat(emergencyAck.get("projectionHash")).isNotEqualTo(emergencyHashRev1);
        assertThat(emergencyAck.get("invalidatedReason")).asString().isNotBlank();
        assertThat(correction.get("revokedQrCodes")).isEqualTo(List.of(qr1));

        // 严禁新名册配旧船长确认：已作废修订不能再签认
        ResponseEntity<Map> stale = as(OWNER, OWNER_PW).postForEntity(
                "/api/crew/revisions/" + rev1 + "/ack?party=EMERGENCY", null, Map.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("ROSTER_SUPERSEDED");

        // 应急方补齐后才放行
        assertThat(correction.get("releasable")).isEqualTo(false);
        ack(rev2, "EMERGENCY");
        Map<String, Object> status = rosterStatus(voyageNo);
        assertThat(status.get("releasable")).isEqualTo(true);
        assertThat(activeQr(voyageNo)).isNotEqualTo(qr1);
    }

    // ---------------- 场景五：整份名册重核——唯一 REEFER 轮机员请假 → 资质覆盖失败 ----------------

    @Test
    void whole_roster_requalification_gap_blocks_resign_when_reefer_engineer_leaves() {
        String voyageNo = setupVoyage();
        fixClock(D + "T11:30:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        ackAll(currentRevisionNo(voyageNo));

        // 轮机员王（唯一 REEFER 资质）请假，换上无 REEFER 资质的轮机员赵
        post(DISPATCHER, DISPATCHER_PW, "/api/crew/persons/P-ENG-01/active", Map.of("active", false));
        ResponseEntity<Map> resp = as(OWNER, OWNER_PW).postForEntity(
                "/api/crew/voyages/" + voyageNo + "/corrections",
                Map.of("personNos", List.of("P-CAP-01", "P-ENG-02", "P-SAIL-01"),
                        "reason", "轮机员王请假，赵接替"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("code")).isEqualTo("ROSTER_COVERAGE_FAIL");
        assertThat(String.valueOf(resp.getBody().get("message"))).contains("REEFER");

        // 未产生新版本：当前仍是已放行的 rev1
        Map<String, Object> status = rosterStatus(voyageNo);
        assertThat(((Map<String, Object>) status.get("current")).get("revisionSeq")).isEqualTo(1);
        assertThat(((Map<String, Object>) status.get("current")).get("status")).isEqualTo("RELEASED");
    }

    // ---------------- 场景二补：替补 09:30 准时完成重签，赶上当前窗 ----------------

    @Test
    void substitute_on_time_finishes_resign_at_0930_and_catches_current_window() {
        String voyageNo = setupVoyage();
        fixClock(D + "T09:30:00");
        initRoster(voyageNo, BASE_CREW, List.of("REEFER"));
        registerWindows(voyageNo);
        ackAll(currentRevisionNo(voyageNo));
        String qr1 = activeQr(voyageNo);

        // 09:30 替补船长（09:15 已休息达标）通过五项核验，完成重签
        Map<String, Object> sub = post(OWNER, OWNER_PW,
                "/api/crew/voyages/" + voyageNo + "/substitute",
                Map.of("replacementPersonNo", "P-CAP-02", "reason", "启用替补赶当前窗"));
        String rev2 = (String) ((Map<String, Object>) sub.get("revision")).get("revisionNo");
        assertThat(((Map<String, Object>) sub.get("revision")).get("changeKind")).isEqualTo("SUBSTITUTE");
        assertThat(sub.get("reSignRequired")).isEqualTo(3);
        assertThat(sub.get("revokedQrCodes")).isEqualTo(List.of(qr1));

        // 三方重签齐备后放行，新二维码在当前窗（至 11:00）内 09:30 扫码通过
        ackAll(rev2);
        String qr2 = activeQr(voyageNo);
        assertThat(qr2).isNotEqualTo(qr1);
        Map<String, Object> scan = scan(qr2);
        assertThat(scan.get("verdict")).isEqualTo("PASS");
        assertThat(scan.get("effectiveRevisionNo")).isEqualTo(rev2);

        // 旧二维码再刷仍被拒
        assertThat(scan(qr1).get("reasonCode")).isEqualTo("OLD_CREDENTIAL_REVOKED");
    }

    // ---------------- 辅助 ----------------

    private String setupVoyage() {
        Map<String, Object> body = new HashMap<>();
        body.put("vesselNo", "V001");
        body.put("captainName", "船长张");
        body.put("crewCount", 3);
        body.put("areaCode", "A1");
        body.put("gearType", "GILLNET");
        body.put("plannedStart", D + "T06:00:00");
        body.put("plannedEnd", D + "T18:00:00");
        body.put("items", List.of(Map.of("speciesCode", "COD", "declaredLimitKg", 100)));
        ResponseEntity<Map> resp = as(OWNER, OWNER_PW).postForEntity("/api/permit-applications", body, Map.class);
        Map<String, Object> result = resp.getBody();
        assertThat(((Map<String, Object>) result.get("application")).get("status")).isEqualTo("APPROVED");
        return (String) ((Map<String, Object>) result.get("voyage")).get("voyageNo");
    }

    private void fixClock(String iso) {
        rest.postForEntity("/api/crew/sim/clock", Map.of("at", iso), Map.class);
    }

    private void initRoster(String voyageNo, List<String> persons, List<String> quals) {
        ResponseEntity<Map> resp = as(OWNER, OWNER_PW).postForEntity("/api/crew/rosters",
                Map.of("voyageNo", voyageNo, "personNos", persons,
                        "requiredQualifications", quals, "reason", "初版名册"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void registerWindows(String voyageNo) {
        as(DISPATCHER, DISPATCHER_PW).postForEntity("/api/crew/departure-windows", Map.of(
                "voyageNo", voyageNo, "portCode", "PORT1", "kind", "CURRENT",
                "windowFrom", D + "T08:00:00", "windowTo", D + "T11:00:00"), Map.class);
        as(DISPATCHER, DISPATCHER_PW).postForEntity("/api/crew/departure-windows", Map.of(
                "voyageNo", voyageNo, "portCode", "PORT2", "kind", "ALTERNATIVE",
                "windowFrom", D + "T11:30:00", "windowTo", D + "T12:00:00"), Map.class);
    }

    private String currentRevisionNo(String voyageNo) {
        return (String) ((Map<String, Object>) rosterStatus(voyageNo).get("current")).get("revisionNo");
    }

    private void ackAll(String revisionNo) {
        for (String party : List.of("PORT", "INSURER", "EMERGENCY")) {
            ack(revisionNo, party);
        }
    }

    private void ack(String revisionNo, String party) {
        ResponseEntity<Map> resp = as(OWNER, OWNER_PW)
                .postForEntity("/api/crew/revisions/" + revisionNo + "/ack?party=" + party, null, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> rosterStatus(String voyageNo) {
        return as(OWNER, OWNER_PW)
                .getForEntity("/api/crew/voyages/" + voyageNo + "/roster", Map.class).getBody();
    }

    private String activeQr(String voyageNo) {
        Map<String, Object> credential = (Map<String, Object>) rosterStatus(voyageNo).get("activeCredential");
        assertThat(credential).as("放行后应存在有效二维码").isNotNull();
        return (String) credential.get("qrCode");
    }

    private Map<String, Object> scan(String qr) {
        return rest.postForEntity("/api/crew/gate/scan", Map.of("qrCode", qr), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> acks(String revisionNo) {
        List<Map<String, Object>> list = as(OWNER, OWNER_PW)
                .getForEntity("/api/crew/revisions/" + revisionNo + "/acks", List.class).getBody();
        Map<String, Object> byParty = new HashMap<>();
        for (Map<String, Object> a : list) {
            byParty.put((String) a.get("party"), a);
        }
        return byParty;
    }

    private String hashOf(Map<String, Object> acks, String party) {
        return (String) ((Map<String, Object>) acks.get(party)).get("projectionHash");
    }

    private Map<String, Object> ackOf(List<Map<String, Object>> acks, String party) {
        return acks.stream().filter(a -> party.equals(a.get("party"))).findFirst().orElseThrow();
    }
}
