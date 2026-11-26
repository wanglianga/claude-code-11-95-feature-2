package com.fishport;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 样例全流程：A 船预留 300kg 鳕鱼、实签 280kg；B 船预留 100kg 比目鱼、实签 90kg。
 * 验证：到港/票据幂等、重称差异、额度扣减与释放、不重复扣减、结案与历史回放。
 */
class VoyageLifecycleTest extends BaseIntegrationTest {

    @Test
    void full_lifecycle_with_reweigh_quota_settlement_and_replay() {
        // ---- A 船：许可 300kg 鳕鱼 ----
        Map<String, Object> aVoyage = approveAndGetVoyage("V001", "A1", "COD", 300);
        String aVoyageNo = (String) aVoyage.get("voyageNo");
        String aPermitNo = aPermitNo(aVoyage);

        // 离港（模拟船舶终端）
        Map<String, Object> dep = as(OWNER, OWNER_PW).postForEntity(
                "/api/sim/vessel-terminal/events",
                Map.of("voyageNo", aVoyageNo, "eventType", "DEPART", "eventId", "E-A-1"), Map.class).getBody();
        assertThat(((Map<String, Object>) dep.get("payload")).get("state")).isEqualTo("DEPARTED");

        // 返港 + 重复到港：只返回原处理结果
        Map<String, Object> arr1 = terminalArrive(aVoyageNo, "E-A-2");
        Map<String, Object> arr2 = terminalArrive(aVoyageNo, "E-A-2-RETRY");
        assertThat(arr2.get("duplicate")).isEqualTo(true);
        assertThat(((Map<String, Object>) arr2.get("payload")).get("state")).isEqualTo("RETURNED");
        // 重复到港返回原处理结果：同一航次、同一到港时刻（秒级一致，存储精度微秒）
        String t1s = (String) ((Map<String, Object>) arr1.get("payload")).get("returnedAt");
        String t2s = (String) ((Map<String, Object>) arr2.get("payload")).get("returnedAt");
        assertThat(t1s.substring(0, 19)).isEqualTo(t2s.substring(0, 19));

        // 申报卸货 280kg 鳕鱼
        Number aRecordId = declareUnload(aVoyageNo, "COD", 280);

        // 地磅票据 T-A-1 净重 282（毛 292 皮 10）
        Map<String, Object> t1 = uploadTicket("T-A-1", aVoyageNo, "COD", 292, 10);
        assertThat(t1.get("duplicate")).isEqualTo(false);
        assertThat(((Number) ((Map<String, Object>) t1.get("payload")).get("netKg")).doubleValue()).isEqualTo(282.0);

        // 重传同一票据：返回原处理结果
        Map<String, Object> t1dup = uploadTicket("T-A-1", aVoyageNo, "COD", 292, 10);
        assertThat(t1dup.get("duplicate")).isEqualTo(true);
        assertThat(((Map<String, Object>) t1dup.get("payload")).get("ticketNo")).isEqualTo("T-A-1");

        // 重量争议 → 复称票据 T-A-2 净重 280（重称差异 282→280）
        raiseDispute(aVoyageNo, aRecordId, "WEIGHT_DISPUTE", "船方对 282kg 有异议");
        Map<String, Object> t2 = uploadTicket("T-A-2", aVoyageNo, "COD", 290, 10);
        assertThat(((Number) ((Map<String, Object>) t2.get("payload")).get("netKg")).doubleValue()).isEqualTo(280.0);
        // 复称后争议自动办结
        List<Map<String, Object>> disputes = as(OWNER, OWNER_PW)
                .getForEntity("/api/voyages/" + aVoyageNo + "/disputes", List.class).getBody();
        assertThat(disputes).hasSize(1);
        assertThat(disputes.get(0).get("status")).isEqualTo("RESOLVED");

        // 签收 280kg：扣减 280、释放未捕 20
        Map<String, Object> sign = as(UNLOADER, UNLOADER_PW)
                .postForEntity("/api/unload-records/" + aRecordId + "/sign", null, Map.class).getBody();
        assertThat(sign.get("duplicate")).isEqualTo(false);
        // 重复签收：不重复扣减
        Map<String, Object> signDup = as(UNLOADER, UNLOADER_PW)
                .postForEntity("/api/unload-records/" + aRecordId + "/sign", null, Map.class).getBody();
        assertThat(signDup.get("duplicate")).isEqualTo(true);

        // ---- B 船：许可 100kg 比目鱼，实签 90kg ----
        Map<String, Object> bVoyage = approveAndGetVoyage("V002", "A1", "FLOUNDER", 100);
        String bVoyageNo = (String) bVoyage.get("voyageNo");
        as(OWNER, OWNER_PW).postForEntity("/api/sim/vessel-terminal/events",
                Map.of("voyageNo", bVoyageNo, "eventType", "DEPART", "eventId", "E-B-1"), Map.class);
        terminalArrive(bVoyageNo, "E-B-2");
        Number bRecordId = declareUnload(bVoyageNo, "FLOUNDER", 90);
        uploadTicket("T-B-1", bVoyageNo, "FLOUNDER", 95, 5);
        as(UNLOADER, UNLOADER_PW).postForEntity("/api/unload-records/" + bRecordId + "/sign", null, Map.class);

        // ---- 额度账终态：A1/COD 已用 280、保留 0（释放 20）；A1/FLOUNDER 已用 90、保留 0（释放 10）----
        Map<String, Object> codAccount = accountOf("A1", "COD");
        assertThat(((Number) codAccount.get("usedKg")).doubleValue()).isEqualTo(280.0);
        assertThat(((Number) codAccount.get("reservedKg")).doubleValue()).isEqualTo(0.0);
        Map<String, Object> flounderAccount = accountOf("A1", "FLOUNDER");
        assertThat(((Number) flounderAccount.get("usedKg")).doubleValue()).isEqualTo(90.0);
        assertThat(((Number) flounderAccount.get("reservedKg")).doubleValue()).isEqualTo(0.0);

        // 流水：A 船 RESERVE 300 + DEDUCT 280 + RELEASE 20，共 3 条（无重复扣减）
        List<Map<String, Object>> codLedger = as(OWNER, OWNER_PW).getForEntity(
                "/api/quota-accounts/" + codAccount.get("id") + "/ledger", List.class).getBody();
        assertThat(codLedger).hasSize(3);
        assertThat(codLedger.stream().map(e -> (String) e.get("entryType")).toList())
                .containsExactly("RESERVE", "DEDUCT", "RELEASE");
        assertThat(((Number) codLedger.get(2).get("amountKg")).doubleValue()).isEqualTo(20.0);

        // ---- 结案 ----
        Map<String, Object> closeA = as(OWNER, OWNER_PW)
                .postForEntity("/api/voyages/" + aVoyageNo + "/close", null, Map.class).getBody();
        assertThat(((Map<String, Object>) closeA.get("payload")).get("state")).isEqualTo("CLOSED");
        as(OWNER, OWNER_PW).postForEntity("/api/voyages/" + bVoyageNo + "/close", null, Map.class);

        // ---- 历史许可回放：时间线完整且流水自洽 ----
        Map<String, Object> replay = as(OWNER, OWNER_PW)
                .getForEntity("/api/permits/" + aPermitNo + "/replay", Map.class).getBody();
        assertThat(replay.get("ledgerConsistent")).isEqualTo(true);
        List<String> eventTypes = ((List<Map<String, Object>>) replay.get("timeline")).stream()
                .map(e -> (String) e.get("type")).toList();
        assertThat(eventTypes).contains("PERMIT_ISSUED", "DEPART", "ARRIVE", "WEIGH_TICKET",
                "REWEIGH_TICKET", "SIGN", "RESERVE", "DEDUCT", "RELEASE", "CLOSE");

        // 监管回执已生成且可确认
        List<Map<String, Object>> receipts = (List<Map<String, Object>>) replay.get("receipts");
        assertThat(receipts).isNotEmpty();
        String receiptNo = (String) receipts.get(0).get("receiptNo");
        Map<String, Object> ack = rest.postForEntity(
                "/api/sim/regulator/receipts/" + receiptNo + "/ack", null, Map.class).getBody();
        assertThat(ack.get("status")).isEqualTo("ACKED");
    }

    @Test
    void zero_catch_voyage_releases_full_reservation_at_close() {
        Map<String, Object> voyage = approveAndGetVoyage("V003", "A1", "HAIRTAIL", 50);
        String voyageNo = (String) voyage.get("voyageNo");
        as(OWNER, OWNER_PW).postForEntity("/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "DEPART", "eventId", "E-C-1"), Map.class);
        terminalArrive(voyageNo, "E-C-2");
        // 零渔获直接结案
        Map<String, Object> close = as(OWNER, OWNER_PW)
                .postForEntity("/api/voyages/" + voyageNo + "/close", null, Map.class).getBody();
        assertThat(((Map<String, Object>) close.get("payload")).get("state")).isEqualTo("CLOSED");
        Map<String, Object> account = accountOf("A1", "HAIRTAIL");
        assertThat(((Number) account.get("reservedKg")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) account.get("usedKg")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void depart_twice_returns_original_result() {
        Map<String, Object> voyage = approveAndGetVoyage("V001", "A1", "COD", 100);
        String voyageNo = (String) voyage.get("voyageNo");
        as(OWNER, OWNER_PW).postForEntity("/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "DEPART", "eventId", "E-D-1"), Map.class);
        Map<String, Object> dup = as(OWNER, OWNER_PW).postForEntity("/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "DEPART", "eventId", "E-D-1-RETRY"), Map.class).getBody();
        assertThat(dup.get("duplicate")).isEqualTo(true);
        assertThat(((Map<String, Object>) dup.get("payload")).get("state")).isEqualTo("DEPARTED");
    }

    // ---------------- 辅助 ----------------

    private Map<String, Object> approveAndGetVoyage(String vesselNo, String area, String species, int limit) {
        ResponseEntity<Map> resp = applyPermit(vesselNo, area, "GILLNET",
                tomorrow0600(), tomorrow0600().plusHours(6), Map.of(species, limit));
        Map<String, Object> body = resp.getBody();
        assertThat(((Map<String, Object>) body.get("application")).get("status"))
                .as("申请应获批: %s", body.get("application")).isEqualTo("APPROVED");
        Map<String, Object> voyage = new java.util.HashMap<>((Map<String, Object>) body.get("voyage"));
        voyage.put("permitNo", ((Map<String, Object>) body.get("permit")).get("permitNo"));
        return voyage;
    }

    private String aPermitNo(Map<String, Object> voyage) {
        return (String) voyage.get("permitNo");
    }

    private Map<String, Object> terminalArrive(String voyageNo, String eventId) {
        return rest.postForEntity("/api/sim/vessel-terminal/events",
                Map.of("voyageNo", voyageNo, "eventType", "ARRIVE", "eventId", eventId), Map.class).getBody();
    }

    private Number declareUnload(String voyageNo, String species, int kg) {
        Map<String, Object> record = as(UNLOADER, UNLOADER_PW).postForEntity(
                "/api/voyages/" + voyageNo + "/unload-records",
                Map.of("speciesCode", species, "declaredKg", kg), Map.class).getBody();
        return (Number) record.get("id");
    }

    private Map<String, Object> uploadTicket(String ticketNo, String voyageNo, String species, int gross, int tare) {
        return rest.postForEntity("/api/sim/weighbridge/tickets",
                Map.of("ticketNo", ticketNo, "voyageNo", voyageNo, "speciesCode", species,
                        "grossKg", gross, "tareKg", tare),
                Map.class).getBody();
    }

    private void raiseDispute(String voyageNo, Number recordId, String type, String desc) {
        ResponseEntity<Map> resp = as(OWNER, OWNER_PW).postForEntity("/api/voyages/" + voyageNo + "/disputes",
                Map.of("unloadRecordId", recordId, "type", type, "description", desc), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> accountOf(String area, String species) {
        List<Map<String, Object>> accounts = as(OWNER, OWNER_PW)
                .getForEntity("/api/quota-accounts", List.class).getBody();
        return accounts.stream()
                .filter(a -> area.equals(a.get("areaCode")) && species.equals(a.get("speciesCode")))
                .findFirst().orElseThrow();
    }
}
