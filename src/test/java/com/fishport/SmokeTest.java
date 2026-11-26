package com.fishport;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 冒烟：上下文加载、Swagger 文档可用、未认证被拒、模拟端免登录 */
class SmokeTest extends BaseIntegrationTest {

    @Test
    void context_loads_and_openapi_available() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("渔业许可与卸港预约服务");
    }

    @Test
    void business_endpoint_requires_auth() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/quota-accounts", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reference_data_seeded() {
        java.util.List<?> list = as(OWNER, OWNER_PW)
                .getForEntity("/api/quota-accounts", java.util.List.class).getBody();
        assertThat(list).hasSize(5); // 5 个 海区+物种+年度 额度账
    }
}
