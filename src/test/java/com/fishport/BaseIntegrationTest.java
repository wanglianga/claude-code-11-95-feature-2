package com.fishport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    protected static final String OWNER = "owner";
    protected static final String OWNER_PW = "owner123";
    protected static final String DISPATCHER = "dispatcher";
    protected static final String DISPATCHER_PW = "dispatcher123";
    protected static final String OFFICER = "officer";
    protected static final String OFFICER_PW = "officer123";
    protected static final String UNLOADER = "unloader";
    protected static final String UNLOADER_PW = "unloader123";
    protected static final String SERVICE = "service";
    protected static final String SERVICE_PW = "service123";
    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PW = "admin123";

    protected TestRestTemplate as(String user, String password) {
        return rest.withBasicAuth(user, password);
    }

    /** 提交许可申请，返回响应 JSON */
    protected ResponseEntity<Map> applyPermit(String vesselNo, String areaCode, String gearType,
                                              LocalDateTime start, LocalDateTime end,
                                              Map<String, Number> speciesLimits) {
        List<Map<String, Object>> items = speciesLimits.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("speciesCode", e.getKey());
                    m.put("declaredLimitKg", e.getValue());
                    return m;
                })
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("vesselNo", vesselNo);
        body.put("captainName", "船长甲");
        body.put("crewCount", 8);
        body.put("crewList", "船员乙,船员丙");
        body.put("areaCode", areaCode);
        body.put("plannedStart", start.toString());
        body.put("plannedEnd", end.toString());
        body.put("gearType", gearType);
        body.put("items", items);
        return as(OWNER, OWNER_PW).postForEntity("/api/permit-applications", body, Map.class);
    }

    /** 明天 06:00 */
    protected LocalDateTime tomorrow0600() {
        return LocalDateTime.now().plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0);
    }

    protected Map<String, Object> post(String user, String pw, String url, Object body) {
        return as(user, pw).postForEntity(url, body, Map.class).getBody();
    }

    protected ResponseEntity<Map> postForStatus(String user, String pw, String url, Object body) {
        return as(user, pw).postForEntity(url, body, Map.class);
    }
}
