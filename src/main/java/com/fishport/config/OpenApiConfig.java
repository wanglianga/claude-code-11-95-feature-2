package com.fishport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fishportOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("渔业许可与卸港预约服务")
                        .version("1.0.0")
                        .description("""
                                出海前许可校验（有效期/海区/季节/渔具/物种额度，额度不跨海区或物种合并）、
                                航次状态机（离港-返港-分物种卸货-复称-结案）、额度账（批复保留、签收扣减并释放未捕部分）、
                                卸港预约双模式（固定时段 / 滚动叫号）、幂等到港与地磅票据、争议阻断结案、
                                船员名册替换与字段投影确认（N-5：三方投影回执失效/沿用、闸口二维码、离港窗、待放行责任）、
                                模拟船舶终端 / 港口地磅 / 监管回执。
                                测试账号：owner/owner123(船主)、dispatcher/dispatcher123(调度员)、
                                officer/officer123(执法)、unloader/unloader123(卸货员)、
                                service/service123(客服)、admin/admin123(管理部门)。
                                """))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
