package com.novinkish.peygir.config;

import com.novinkish.peygir.repository.UserRepository;
import com.novinkish.peygir.security.SessionRefreshFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * کنترل دسترسی لایه‌ی اول (بر اساس دسترسی‌های هر کاربر: PERM_xxx).
 * کنترل دقیق‌تر (تیم، وضعیت گزارش، مالکیت) در ReportService انجام می‌شود.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
            .addFilterBefore(new SessionRefreshFilter(users), AuthorizationFilter.class)
            .headers(h -> h.httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(false)
                .requestMatcher(r -> r.isSecure() && !isLocalHost(r.getServerName()))))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/css/**", "/js/**", "/fonts/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasAuthority("PERM_ADMIN_PANEL")
                .requestMatchers("/approvals").hasAuthority("PERM_TEAM_APPROVE")
                .requestMatchers("/dashboard/export").hasAuthority("PERM_EXPORT")
                .requestMatchers("/dashboard/**").hasAnyAuthority("PERM_TEAM_STATS", "PERM_VIEW_ALL")
                .requestMatchers("/reports/all").hasAuthority("PERM_VIEW_ALL")
                .requestMatchers("/reports/export", "/reports/*/export").hasAuthority("PERM_EXPORT")
                .requestMatchers("/reports/*/approve", "/reports/*/return", "/reports/*/reject")
                    .hasAuthority("PERM_TEAM_APPROVE")
                .requestMatchers("/reports/*/review", "/reports/*/close").hasAuthority("PERM_REVIEW_CLOSE")
                .requestMatchers("/reports/new", "/reports/*/edit", "/reports/*/submit")
                    .hasAuthority("PERM_REPORT_CREATE")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/reports", "/reports/*")
                    .hasAuthority("PERM_REPORT_CREATE")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(l -> l
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    // HSTS روی localhost می‌تواند HTTP:8080 را قبل از هدایت، به HTTPS:8080 تبدیل کند.
    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }
}
