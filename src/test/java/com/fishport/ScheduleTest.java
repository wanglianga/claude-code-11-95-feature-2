package com.fishport;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 样例港口 06:00-08:00 一条卸货线 + 一台地磅，作业间清场 5 分钟。
 * A 船原约 06:30-07:10、最晚 07:45，改 06:25 到港，卸货 40 分钟；
 * B 船 06:20 到港，卸货 20 分钟、最晚 07:35。
 * 固定预约：A 06:30-07:10、B 07:15-07:35；滚动叫号：B 06:20-06:40、A 06:45-07:25。
 */
class ScheduleTest extends BaseIntegrationTest {

    private LocalDate day() {
        return LocalDate.now().plusDays(1);
    }

    private LocalDateTime at(int hh, int mm) {
        return day().atTime(hh, mm);
    }

    @Test
    void fixed_mode_matches_sample_and_cleanup_occupies_line() {
        String aVoyage = approveVoyage("V001", "COD", 300);
        String bVoyage = approveVoyage("V002", "FLOUNDER", 100);
        // A 原约 06:30（40 分钟，最晚 07:45）；B 申请 07:10（20 分钟，最晚 07:35）
        book(aVoyage, "FIXED", at(6, 30), null, 40, at(7, 45), 300);
        book(bVoyage, "FIXED", at(7, 10), null, 20, at(7, 35), 100);

        List<Map<String, Object>> computed = compute("FIXED");
        Map<String, Object> a = byVoyageNo(computed, aVoyage);
        Map<String, Object> b = byVoyageNo(computed, bVoyage);

        // 固定预约：A 06:30-07:10、B 07:15-07:35（B 起点 = A 终点 + 清场 5 分钟）
        assertThat((String) a.get("slotStart")).endsWith("06:30:00");
        assertThat((String) a.get("slotEnd")).endsWith("07:10:00");
        assertThat((String) b.get("slotStart")).endsWith("07:15:00");
        assertThat((String) b.get("slotEnd")).endsWith("07:35:00");
        assertThat(a.get("deadlineBreach")).isEqualTo(false);
        assertThat(b.get("deadlineBreach")).isEqualTo(false);
        assertThat(a.get("seq")).isEqualTo(1);
        assertThat(b.get("seq")).isEqualTo(2);

        // A 提早到港（06:25 打卡）仍按已通知时段 06:30 开工
        Map<String, Object> aAfter = as(DISPATCHER, DISPATCHER_PW).postForEntity(
                "/api/appointments/" + a.get("appointmentNo") + "/checkin",
                Map.of("actualArrival", at(6, 25).toString()), Map.class).getBody();
        assertThat((String) aAfter.get("slotStart")).endsWith("06:30:00");
    }

    @Test
    void rolling_mode_matches_sample_by_arrival_order() {
        String aVoyage = approveVoyage("V001", "COD", 300);
        String bVoyage = approveVoyage("V002", "FLOUNDER", 100);
        // 滚动叫号：B 06:20 到港（20 分钟），A 06:25 到港（40 分钟）
        book(aVoyage, "ROLLING", null, at(6, 25), 40, at(7, 45), 300);
        book(bVoyage, "ROLLING", null, at(6, 20), 20, at(7, 35), 100);

        List<Map<String, Object>> computed = compute("ROLLING");
        Map<String, Object> a = byVoyageNo(computed, aVoyage);
        Map<String, Object> b = byVoyageNo(computed, bVoyage);

        // 先卸 B：06:20-06:40；清场 5 分钟后卸 A：06:45-07:25，两船均满足期限
        assertThat((String) b.get("slotStart")).endsWith("06:20:00");
        assertThat((String) b.get("slotEnd")).endsWith("06:40:00");
        assertThat((String) a.get("slotStart")).endsWith("06:45:00");
        assertThat((String) a.get("slotEnd")).endsWith("07:25:00");
        assertThat(b.get("seq")).isEqualTo(1);
        assertThat(a.get("seq")).isEqualTo(2);
        assertThat(a.get("deadlineBreach")).isEqualTo(false);
        assertThat(b.get("deadlineBreach")).isEqualTo(false);
    }

    @Test
    void fixed_late_arrival_reschedules_to_tail_and_notifies() {
        String aVoyage = approveVoyage("V001", "COD", 300);
        String bVoyage = approveVoyage("V002", "FLOUNDER", 100);
        book(aVoyage, "FIXED", at(6, 30), null, 40, at(7, 45), 300);
        book(bVoyage, "FIXED", at(7, 10), null, 20, at(7, 35), 100);
        List<Map<String, Object>> computed = compute("FIXED");
        String aApptNo = (String) byVoyageNo(computed, aVoyage).get("appointmentNo");

        // A 迟到 07:30 才到港：07:30+40 分钟将超过最晚 07:45 → 改期队尾
        Map<String, Object> aAfter = as(DISPATCHER, DISPATCHER_PW).postForEntity(
                "/api/appointments/" + aApptNo + "/checkin",
                Map.of("actualArrival", at(7, 30).toString()), Map.class).getBody();
        assertThat(aAfter.get("status")).isEqualTo("RESCHEDULED");
        assertThat(aAfter.get("seq")).isEqualTo(2);
        assertThat(aAfter.get("deadlineBreach")).isEqualTo(true);
        assertThat((String) aAfter.get("slotStart")).endsWith("07:35:00"); // B 07:10-07:30 之后 + 清场 5 分钟

        // 改期已通知船主
        Number aVoyageId = voyageIdOf(aVoyage);
        List<Map<String, Object>> notes = as(DISPATCHER, DISPATCHER_PW)
                .getForEntity("/api/voyages/" + aVoyageId + "/notifications", List.class).getBody();
        assertThat(notes.stream().map(n -> (String) n.get("type")).toList()).contains("RESCHEDULED");
    }

    @Test
    void rolling_checkin_changes_published_order_and_renotifies() {
        String aVoyage = approveVoyage("V001", "COD", 300);
        String bVoyage = approveVoyage("V002", "FLOUNDER", 100);
        // 公布顺序：A（计划 06:20）在前，B（计划 06:25）在后
        book(aVoyage, "ROLLING", null, at(6, 20), 40, at(7, 45), 300);
        book(bVoyage, "ROLLING", null, at(6, 25), 20, at(7, 35), 100);
        List<Map<String, Object>> computed = compute("ROLLING");
        assertThat(byVoyageNo(computed, aVoyage).get("seq")).isEqualTo(1);
        String bApptNo = (String) byVoyageNo(computed, bVoyage).get("appointmentNo");

        // B 实际 06:10 到港，滚动叫号改变已公布顺序
        Map<String, Object> bAfter = as(DISPATCHER, DISPATCHER_PW).postForEntity(
                "/api/appointments/" + bApptNo + "/checkin",
                Map.of("actualArrival", at(6, 10).toString()), Map.class).getBody();
        assertThat(bAfter.get("seq")).isEqualTo(1);
        assertThat((String) bAfter.get("slotStart")).endsWith("06:10:00");

        // A 被挤到第二且收到重新通知
        Number aVoyageId = voyageIdOf(aVoyage);
        List<Map<String, Object>> notes = as(DISPATCHER, DISPATCHER_PW)
                .getForEntity("/api/voyages/" + aVoyageId + "/notifications", List.class).getBody();
        assertThat(notes.stream().map(n -> (String) n.get("type")).toList()).contains("QUEUE_CHANGED");
    }

    // ---------------- 辅助 ----------------

    private String approveVoyage(String vesselNo, String species, int limit) {
        var resp = applyPermit(vesselNo, "A1", "GILLNET",
                tomorrow0600(), tomorrow0600().plusHours(6), Map.of(species, limit));
        Map<String, Object> voyage = (Map<String, Object>) resp.getBody().get("voyage");
        return (String) voyage.get("voyageNo");
    }

    private void book(String voyageNo, String mode, LocalDateTime requestedStart,
                      LocalDateTime plannedArrival, int durationMin, LocalDateTime latestFinish, int expectedKg) {
        Map<String, Object> body = new HashMap<>();
        body.put("voyageNo", voyageNo);
        body.put("portCode", "PORT1");
        body.put("mode", mode);
        if (requestedStart != null) {
            body.put("requestedStart", requestedStart.toString());
        }
        if (plannedArrival != null) {
            body.put("plannedArrival", plannedArrival.toString());
        }
        body.put("durationMin", durationMin);
        body.put("latestFinish", latestFinish.toString());
        body.put("expectedUnloadKg", expectedKg);
        var resp = as(DISPATCHER, DISPATCHER_PW).postForEntity("/api/appointments", body, Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("预约应成功: %s", resp.getBody()).isTrue();
    }

    private List<Map<String, Object>> compute(String mode) {
        var resp = as(DISPATCHER, DISPATCHER_PW).postForEntity("/api/appointments/compute",
                Map.of("portCode", "PORT1", "date", day().toString(), "mode", mode), List.class);
        return resp.getBody();
    }

    private Map<String, Object> byVoyageNo(List<Map<String, Object>> appointments, String voyageNo) {
        Number voyageId = voyageIdOf(voyageNo);
        return appointments.stream()
                .filter(a -> ((Number) a.get("voyageId")).longValue() == voyageId.longValue())
                .findFirst().orElseThrow();
    }

    private Number voyageIdOf(String voyageNo) {
        Map<String, Object> voyage = as(DISPATCHER, DISPATCHER_PW)
                .getForEntity("/api/voyages/" + voyageNo, Map.class).getBody();
        return (Number) voyage.get("id");
    }
}
