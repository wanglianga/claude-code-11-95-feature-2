package com.fishport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 争议阻断结案：重量争议/混装来源不清/许可证过期；客服备注不能当成执法事实 */
class DisputeClosureTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void mixed_load_dispute_blocks_closure_until_enforcement_resolves() {
        String voyageNo = approveDepartArriveSign("V001", "COD", 300, 280);

        // 混装来源不清：不能结案
        as(UNLOADER, UNLOADER_PW).postForEntity("/api/voyages/" + voyageNo + "/disputes",
                Map.of("type", "MIXED_LOAD", "description", "舱内混装，来源不清"), Map.class);
        ResponseEntity<Map> blocked = postForStatus(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) blocked.getBody().get("code")).isEqualTo("DISPUTE_OPEN");

        // 客服备注"已核实"不能当成执法事实，结案仍被阻断
        Map<String, Object> serviceNote = as(SERVICE, SERVICE_PW).postForEntity(
                "/api/voyages/" + voyageNo + "/notes",
                Map.of("content", "客服电话核实：没问题"), Map.class).getBody();
        assertThat(serviceNote.get("enforcementFact")).isEqualTo(false);
        ResponseEntity<Map> stillBlocked = postForStatus(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(stillBlocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 执法人员备注是执法事实；执法办结争议后才能结案
        Map<String, Object> officerNote = as(OFFICER, OFFICER_PW).postForEntity(
                "/api/voyages/" + voyageNo + "/notes",
                Map.of("content", "现场核查：混装部分已分离溯源"), Map.class).getBody();
        assertThat(officerNote.get("enforcementFact")).isEqualTo(true);

        Number disputeId = openDisputeId(voyageNo);
        as(OFFICER, OFFICER_PW).postForEntity("/api/disputes/" + disputeId + "/resolve",
                Map.of("resolution", "混装渔获已分离称重溯源，准予结案"), Map.class);
        Map<String, Object> closed = post(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(((Map<String, Object>) closed.get("payload")).get("state")).isEqualTo("CLOSED");
    }

    @Test
    void zero_catch_species_released_when_voyage_closes() {
        // 两物种许可：FLOUNDER 签收 90kg，HAIRTAIL 零渔获未卸货
        String voyageNo = approveVoyage("V002", Map.of("FLOUNDER", 100, "HAIRTAIL", 50));
        departArrive(voyageNo);
        Number recordId = declareAndWeigh(voyageNo, "FLOUNDER", 90);
        as(UNLOADER, UNLOADER_PW).postForEntity("/api/unload-records/" + recordId + "/sign", null, Map.class);
        // HAIRTAIL 无卸货记录（零渔获）→ 结案时全额释放保留额度
        Map<String, Object> closed = post(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(((Map<String, Object>) closed.get("payload")).get("state")).isEqualTo("CLOSED");
        Map<String, Object> hairtail = accountOf("A1", "HAIRTAIL");
        assertThat(((Number) hairtail.get("reservedKg")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) hairtail.get("usedKg")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void expired_permit_blocks_closure_and_auto_raises_dispute() {
        String voyageNo = approveDepartArriveSign("V001", "COD", 300, 280);
        // 直接把许可有效期改到过去（模拟作业中超期）
        jdbc.update("UPDATE permits SET valid_to = now() - interval '1 hour' WHERE permit_no IN "
                + "(SELECT p.permit_no FROM permits p JOIN voyages v ON v.permit_id = p.id WHERE v.voyage_no = ?)",
                voyageNo);

        // 结案被阻断，并自动登记 PERMIT_EXPIRED 争议
        ResponseEntity<Map> blocked = postForStatus(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) blocked.getBody().get("code")).isEqualTo("PERMIT_EXPIRED");
        List<Map<String, Object>> disputes = as(OWNER, OWNER_PW)
                .getForEntity("/api/voyages/" + voyageNo + "/disputes", List.class).getBody();
        assertThat(disputes).hasSize(1);
        assertThat(disputes.get(0).get("type")).isEqualTo("PERMIT_EXPIRED");
        assertThat(disputes.get(0).get("status")).isEqualTo("OPEN");

        // 执法办结后放行结案
        as(OFFICER, OFFICER_PW).postForEntity("/api/disputes/" + disputes.get(0).get("id") + "/resolve",
                Map.of("resolution", "已核查：超期系避风延误，渔获在有效期内捕获，准予结案"), Map.class);
        Map<String, Object> closed = post(OWNER, OWNER_PW, "/api/voyages/" + voyageNo + "/close", null);
        assertThat(((Map<String, Object>) closed.get("payload")).get("state")).isEqualTo("CLOSED");
    }

    // ---------------- 辅助 ----------------

    private String approveVoyage(String vesselNo, Map<String, Number> speciesLimits) {
        var resp = applyPermit(vesselNo, "A1", "GILLNET",
                tomorrow0600(), tomorrow0600().plusHours(6), speciesLimits);
        Map<String, Object> voyage = (Map<String, Object>) resp.getBody().get("voyage");
        return (String) voyage.get("voyageNo");
    }

    private void departArrive(String voyageNo) {
        as(OWNER, OWNER_PW).postForEntity("/api/voyages/" + voyageNo + "/depart", null, Map.class);
        as(OWNER, OWNER_PW).postForEntity("/api/voyages/" + voyageNo + "/arrive", null, Map.class);
    }

    private Number declareAndWeigh(String voyageNo, String species, int kg) {
        Map<String, Object> record = as(UNLOADER, UNLOADER_PW).postForEntity(
                "/api/voyages/" + voyageNo + "/unload-records",
                Map.of("speciesCode", species, "declaredKg", kg), Map.class).getBody();
        Number recordId = (Number) record.get("id");
        rest.postForEntity("/api/sim/weighbridge/tickets",
                Map.of("ticketNo", "T-" + voyageNo + "-" + species, "voyageNo", voyageNo,
                        "speciesCode", species, "grossKg", kg + 10, "tareKg", 10),
                Map.class);
        return recordId;
    }

    /** 许可 → 离港 → 返港 → 申报 → 称重 → 签收，一站走完 */
    private String approveDepartArriveSign(String vesselNo, String species, int limit, int actualKg) {
        String voyageNo = approveVoyage(vesselNo, Map.of(species, limit));
        departArrive(voyageNo);
        Number recordId = declareAndWeigh(voyageNo, species, actualKg);
        as(UNLOADER, UNLOADER_PW).postForEntity("/api/unload-records/" + recordId + "/sign", null, Map.class);
        return voyageNo;
    }

    private Number openDisputeId(String voyageNo) {
        List<Map<String, Object>> disputes = as(OFFICER, OFFICER_PW)
                .getForEntity("/api/voyages/" + voyageNo + "/disputes", List.class).getBody();
        return (Number) disputes.stream().filter(d -> "OPEN".equals(d.get("status")))
                .findFirst().orElseThrow().get("id");
    }

    private Map<String, Object> accountOf(String area, String species) {
        List<Map<String, Object>> accounts = as(OWNER, OWNER_PW)
                .getForEntity("/api/quota-accounts", List.class).getBody();
        return accounts.stream()
                .filter(a -> area.equals(a.get("areaCode")) && species.equals(a.get("speciesCode")))
                .findFirst().orElseThrow();
    }
}
