package com.fishport;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 许可校验：有效期、海区、季节、渔具、物种额度（不跨海区 ++合并）、未结航次 */
class PermitValidationTest extends BaseIntegrationTest {

    @Test
    void approve_valid_application_and_reserve_quota() {
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "GILLNET",
                tomorrow0600(), tomo0600plus6h(), Map.of("COD", 300));
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("application")).isNotNull();
        Map<String, Object> app = (Map<String, Object>) body.get("application");
        assertThat(app.get("status")).isEqualTo("APPROVED");
        Map<String, Object> permit = (Map<String, Object>) body.get("permit");
        assertThat(permit.get("permitNo")).asString().startsWith("PMT-");
        Map<String, Object> voyage = (Map<String, Object>) body.get("voyage");
        assertThat(voyage.get("state")).isEqualTo("APPROVED");

        // 额度账：A1/COD 保留 300
        List<Map<String, Object>> accounts = as(OWNER, OWNER_PW)
                .getForEntity("/api/quota-accounts", List.class).getBody();
        Map<String, Object> cod = accounts.stream()
                .filter(a -> "A1".equals(a.get("areaCode")) && "COD".equals(a.get("speciesCode")))
                .findFirst().orElseThrow();
        assertThat(((Number) cod.get("reservedKg")).doubleValue()).isEqualTo(300.0);
        assertThat(((Number) cod.get("usedKg")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void reject_when_quota_insufficient_in_single_account_even_if_sum_across_areas_enough() {
        // A1/COD 总额 10000，A2/COD 5000，合计 15000；申请 A1 12000 必须驳回（不得跨海区相加）
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "GILLNET",
                tomorrow0600(), tomo0600plus6h(), Map.of("COD", 12000));
        Map<String, Object> app = (Map<String, Object>) resp.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("额度校验失败").contains("不得相加");
    }

    @Test
    void reject_when_quota_insufficient_in_single_species_even_if_other_species_enough() {
        // A1/FLOUNDER 总额 8000；申请 9000 必须驳回（不得与其他物种额度相加）
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "GILLNET",
                tomorrow0600(), tomo0600plus6h(), Map.of("FLOUNDER", 9000));
        Map<String, Object> app = (Map<String, Object>) resp.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("额度校验失败");
    }

    @Test
    void reject_when_gear_forbidden_in_area() {
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "TRAWL",
                tomorrow0600(), tomo0600plus6h(), Map.of("COD", 100));
        Map<String, Object> app = (Map<String, Object>) resp.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("渔具校验失败");
    }

    @Test
    void reject_when_in_closed_season() {
        // A1/COD 禁渔期 05-01 ~ 08-31
        LocalDateTime start = LocalDateTime.of(2027, 5, 10, 6, 0);
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "GILLNET",
                start, start.plusHours(6), Map.of("COD", 100));
        Map<String, Object> app = (Map<String, Object>) resp.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("季节校验失败");
    }

    @Test
    void reject_when_validity_window_invalid() {
        LocalDateTime past = LocalDateTime.now().minusDays(2);
        ResponseEntity<Map> resp = applyPermit("V001", "A1", "GILLNET",
                past, past.plusHours(6), Map.of("COD", 100));
        Map<String, Object> app = (Map<String, Object>) resp.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("有效期校验失败");
    }

    @Test
    void reject_second_permit_while_voyage_open() {
        ResponseEntity<Map> first = applyPermit("V001", "A1", "GILLNET",
                tomorrow0600(), tomo0600plus6h(), Map.of("COD", 300));
        assertThat(((Map<String, Object>) first.getBody().get("application")).get("status")).isEqualTo("APPROVED");

        ResponseEntity<Map> second = applyPermit("V001", "A1", "GILLNET",
                tomorrow0600(), tomo0600plus6h(), Map.of("COD", 100));
        Map<String, Object> app = (Map<String, Object>) second.getBody().get("application");
        assertThat(app.get("status")).isEqualTo("REJECTED");
        assertThat((String) app.get("rejectReasons")).contains("未结航次").contains("不得再获第二张出海许可");
    }

    private LocalDateTime tomo0600plus6h() {
        return tomorrow0600().plusHours(6);
    }
}
