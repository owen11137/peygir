package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.Permission;
import com.novinkish.peygir.domain.Status;
import com.novinkish.peygir.service.ReportService;
import lombok.RequiredArgsConstructor;
import com.novinkish.peygir.security.PeygirUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** مقادیر مشترک همه‌ی صفحه‌ها (کاربر جاری و مسیر برای هایلایت منو). */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModel {
    private final ReportService reportService;

    @ModelAttribute("pendingCount")
    public Long pendingCount(@AuthenticationPrincipal PeygirUser user) {
        return (user != null && user.has(Permission.TEAM_APPROVE)) ? reportService.pendingCountForManager(user) : null;
    }

    @ModelAttribute("me")
    public PeygirUser me(@AuthenticationPrincipal PeygirUser user) { return user; }

    @ModelAttribute("path")
    public String path(HttpServletRequest request) { return request.getRequestURI(); }

    @ModelAttribute("allStatuses")
    public Status[] allStatuses() { return Status.values(); }
}
