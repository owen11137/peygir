package com.novinkish.peygir.security;

import com.novinkish.peygir.domain.AppUser;
import com.novinkish.peygir.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * در هر درخواست، کاربر را دوباره از دیتابیس می‌خواند تا تغییر دسترسی/تیم/وضعیت فعال‌بودن
 * که ادمین می‌دهد بدون نیاز به خروج و ورود دوباره اعمال شود.
 */
public class SessionRefreshFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public SessionRefreshFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PeygirUser p) {
            AppUser fresh = users.findById(p.getId()).orElse(null);
            if (fresh == null || !fresh.isActive() || !fresh.getTeam().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
            } else {
                PeygirUser np = new PeygirUser(fresh);
                UsernamePasswordAuthenticationToken t =
                        UsernamePasswordAuthenticationToken.authenticated(np, null, np.getAuthorities());
                t.setDetails(auth.getDetails());
                ctx.setAuthentication(t);
            }
        }
        chain.doFilter(req, res);
    }
}
