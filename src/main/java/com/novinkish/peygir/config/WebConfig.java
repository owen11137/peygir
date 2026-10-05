package com.novinkish.peygir.config;

import com.novinkish.peygir.security.PeygirUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** تا وقتی کاربر رمز اولیه را عوض نکرده، همه‌ی صفحه‌ها به /password هدایت می‌شوند. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof PeygirUser p && p.isMustChangePassword()) {
                    res.sendRedirect(req.getContextPath() + "/password");
                    return false;
                }
                return true;
            }
        }).excludePathPatterns("/css/**", "/js/**", "/fonts/**", "/password", "/logout", "/login", "/error");
    }
}
