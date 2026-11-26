package com.fishport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 演示用安全模型：HTTP Basic + 内存账号。
 * 角色：OWNER 船主 / DISPATCHER 港口调度员 / ENFORCEMENT 执法人员 / UNLOADER 卸货员 / SERVICE 客服 / ADMIN 管理部门。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("owner").password(encoder.encode("owner123")).roles("OWNER").build(),
                User.withUsername("dispatcher").password(encoder.encode("dispatcher123")).roles("DISPATCHER").build(),
                User.withUsername("officer").password(encoder.encode("officer123")).roles("ENFORCEMENT").build(),
                User.withUsername("unloader").password(encoder.encode("unloader123")).roles("UNLOADER").build(),
                User.withUsername("service").password(encoder.encode("service123")).roles("SERVICE").build(),
                User.withUsername("admin").password(encoder.encode("admin123")).roles("ADMIN", "OWNER",
                        "DISPATCHER", "ENFORCEMENT", "UNLOADER").build());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 模拟端（船舶终端/地磅/监管回执/闸口扫码/模拟时钟）与 API 文档开放
                        .requestMatchers("/api/sim/**", "/api/crew/gate/scan", "/api/crew/sim/clock/**",
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/health").permitAll()
                        // 写操作按角色
                        .requestMatchers(HttpMethod.POST, "/api/permit-applications").hasAnyRole("OWNER", "ADMIN")
                        // N-5 名册：建档与船东决定（休息/替补/更正）仅船主
                        .requestMatchers(HttpMethod.POST, "/api/crew/rosters",
                                "/api/crew/voyages/*/keep-captain-rest", "/api/crew/voyages/*/substitute",
                                "/api/crew/voyages/*/corrections").hasAnyRole("OWNER", "ADMIN")
                        // N-5 离港窗登记与人员请假：港口调度/管理部门；接收方确认登录即可（actor 留痕）
                        .requestMatchers(HttpMethod.POST, "/api/crew/departure-windows",
                                "/api/crew/persons/*/active").hasAnyRole("DISPATCHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/voyages/*/depart", "/api/voyages/*/arrive",
                                "/api/voyages/*/close").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/voyages/*/unload-records").hasAnyRole("UNLOADER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/unload-records/**").hasAnyRole("UNLOADER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/weigh-tickets").hasAnyRole("UNLOADER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/appointments", "/api/appointments/compute",
                                "/api/appointments/*/checkin", "/api/appointments/*/finish")
                        .hasAnyRole("DISPATCHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/disputes/*/resolve").hasAnyRole("ENFORCEMENT", "ADMIN")
                        // 其余查询与备注/争议登记登录即可
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
