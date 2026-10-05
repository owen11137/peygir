package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.ReasonRepository;
import com.novinkish.peygir.repository.TeamRepository;
import com.novinkish.peygir.repository.UserRepository;
import com.novinkish.peygir.security.PeygirUser;
import com.novinkish.peygir.service.ExcelService;
import com.novinkish.peygir.service.JalaliCalendar;
import com.novinkish.peygir.service.ReportFilter;
import com.novinkish.peygir.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    private final TeamRepository teams;
    private final ReasonRepository reasons;
    private final ExcelService excel;
    private final UserRepository users;

    /** گروه‌بندی اعضا بر اساس تیم برای کمبوباکس «شخص موردنظر». */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UserGroup {
        private final Long teamId;
        private final String teamName;
        private final List<AppUser> users;
    }

    // ---------------------------------------------------------------- لیست‌ها

    /** گزارش‌های من و تیم من (عضو تیم و مدیر تیم). */
    @GetMapping("/reports")
    public String myReports(@AuthenticationPrincipal PeygirUser u, @ModelAttribute("filter") ReportFilter filter,
                            @RequestParam(defaultValue = "all") String scope, Model model) {
        model.addAttribute("reports", service.list(u, filter, "mine".equals(scope)));
        model.addAttribute("pageTitle", "گزارش‌های من و تیم");
        model.addAttribute("mode", "team");
        model.addAttribute("scope", scope);
        lookups(model);
        return "report-list";
    }

    /** همه‌ی گزارش‌های قابل‌دیدن (مدیرعامل، ادمین؛ مدیر تیم: تیم خودش). */
    @GetMapping("/reports/all")
    public String allReports(@AuthenticationPrincipal PeygirUser u, @ModelAttribute("filter") ReportFilter filter, Model model) {
        model.addAttribute("reports", service.list(u, filter, false));
        model.addAttribute("pageTitle", "همه‌ی گزارش‌ها");
        model.addAttribute("mode", "all");
        lookups(model);
        return "report-list";
    }

    @GetMapping("/approvals")
    public String approvals(@AuthenticationPrincipal PeygirUser u, Model model) {
        model.addAttribute("reports", service.pendingForManager(u));
        model.addAttribute("pageTitle", "صف تأیید مدیر تیم");
        model.addAttribute("mode", "approvals");
        model.addAttribute("filter", new ReportFilter());
        lookups(model);
        return "report-list";
    }

    /** خروجی Excel لیست فعلی (با همان فیلترها)؛ pending=true برای صف تأیید مدیر تیم. */
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PeygirUser u, @ModelAttribute ReportFilter filter,
                                         @RequestParam(defaultValue = "all") String scope,
                                         @RequestParam(defaultValue = "false") boolean pending) throws Exception {
        List<Report> list;
        if (pending) {
            if (!u.has(Permission.TEAM_APPROVE)) throw new AccessDeniedException("approvals");
            list = service.pendingForManager(u);
        } else {
            list = service.list(u, filter, "mine".equals(scope));
        }
        return Xlsx.ok("peygir-reports-" + today() + ".xlsx", excel.exportReports(list));
    }

    /** خروجی Excel یک گزارش همراه با تاریخچه‌ی کامل. */
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<byte[]> exportOne(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id) throws Exception {
        Report r = service.getVisible(u, id);
        return Xlsx.ok("peygir-" + r.getTrackingNo() + ".xlsx", excel.exportReportDetail(r, service.timeline(r)));
    }

    private static String today() {
        return JalaliCalendar.format(JalaliCalendar.now().toLocalDate()).replace("/", "-");
    }

    // ---------------------------------------------------------------- ثبت و ویرایش

    @GetMapping("/reports/new")
    public String newForm(@AuthenticationPrincipal PeygirUser u, Model model) {
        model.addAttribute("form", new ReportForm());
        formModel(model, null, u);
        return "report-form";
    }

    @PostMapping("/reports")
    public String create(@AuthenticationPrincipal PeygirUser u, @Valid @ModelAttribute("form") ReportForm form,
                         BindingResult br, @RequestParam(defaultValue = "draft") String action,
                         Model model, RedirectAttributes ra) {
        service.validateForm(u, form, br);
        if (br.hasErrors()) {
            formModel(model, null, u);
            return "report-form";
        }
        boolean submit = "submit".equals(action);
        Report r = service.create(u, form, submit);
        ra.addFlashAttribute("success", submit ? "گزارش ثبت و ارسال شد" : "پیش‌نویس ذخیره شد");
        return "redirect:/reports/" + r.getId();
    }

    @GetMapping("/reports/{id}/edit")
    public String editForm(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id, Model model) {
        Report r = service.getEditable(u, id);
        model.addAttribute("form", ReportForm.from(r));
        formModel(model, r, u);
        return "report-form";
    }

    @PostMapping("/reports/{id}")
    public String update(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                         @Valid @ModelAttribute("form") ReportForm form, BindingResult br,
                         @RequestParam(defaultValue = "draft") String action, Model model, RedirectAttributes ra) {
        Report existing = service.getEditable(u, id);
        service.validateForm(u, form, br);
        if (br.hasErrors()) {
            formModel(model, existing, u);
            return "report-form";
        }
        boolean submit = "submit".equals(action);
        service.update(u, id, form, submit);
        ra.addFlashAttribute("success", submit ? "گزارش ارسال شد" : "تغییرات ذخیره شد");
        return "redirect:/reports/" + id;
    }

    @PostMapping("/reports/{id}/submit")
    public String submit(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id, RedirectAttributes ra) {
        service.submit(u, id);
        ra.addFlashAttribute("success", "گزارش ارسال شد");
        return "redirect:/reports/" + id;
    }

    // ---------------------------------------------------------------- جزئیات و اقدام‌ها

    @GetMapping("/reports/{id}")
    public String detail(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id, Model model) {
        Report r = service.getVisible(u, id);
        model.addAttribute("r", r);
        model.addAttribute("timeline", service.timeline(r));
        model.addAttribute("canEdit", service.canEdit(u, r));
        model.addAttribute("canDecide", service.canDecide(u, r));
        model.addAttribute("canReview", service.canReview(u, r));
        model.addAttribute("canClose", service.canClose(u, r));
        return "report-detail";
    }

    @PostMapping("/reports/{id}/approve")
    public String approve(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                          @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.approve(u, id, note);
        ra.addFlashAttribute("success", "گزارش تأیید شد و برای مدیریت ارشد ارسال شد");
        return "redirect:/approvals";
    }

    @PostMapping("/reports/{id}/return")
    public String returnForFix(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.returnForFix(u, id, note);
        ra.addFlashAttribute("success", "گزارش برای اصلاح برگشت داده شد");
        return "redirect:/approvals";
    }

    @PostMapping("/reports/{id}/reject")
    public String reject(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.reject(u, id, note);
        ra.addFlashAttribute("success", "گزارش رد شد");
        return "redirect:/approvals";
    }

    @PostMapping("/reports/{id}/review")
    public String review(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.startReview(u, id, note);
        ra.addFlashAttribute("success", "وضعیت به «در حال بررسی» تغییر کرد");
        return "redirect:/reports/" + id;
    }

    @PostMapping("/reports/{id}/close")
    public String close(@AuthenticationPrincipal PeygirUser u, @PathVariable Long id,
                        @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.close(u, id, note);
        ra.addFlashAttribute("success", "گزارش بسته شد");
        return "redirect:/reports/" + id;
    }

    // ---------------------------------------------------------------- کمکی

    private void lookups(Model model) {
        model.addAttribute("teams", teams.findAllByOrderByName());
        model.addAttribute("reasons", reasons.findAllByOrderBySortOrder());
    }

    private void formModel(Model model, Report editing, PeygirUser me) {
        model.addAttribute("reasons", reasons.findByActiveTrueOrderBySortOrder());
        model.addAttribute("methods", ContactMethod.values());
        model.addAttribute("userGroups", userGroups(me));
        model.addAttribute("editing", editing);
    }

    private List<UserGroup> userGroups(PeygirUser me) {
        Map<Long, UserGroup> groups = new LinkedHashMap<>();
        for (Team t : teams.findByActiveTrueOrderByName()) {
            if (!t.getId().equals(me.getTeamId())) groups.put(t.getId(), new UserGroup(t.getId(), t.getName(), new ArrayList<>()));
        }
        for (AppUser u : users.findAllByOrderByFullName()) {
            if (!u.isActive() || !(u.has(Permission.REPORT_CREATE) || u.has(Permission.TEAM_APPROVE))) continue;
            UserGroup g = groups.get(u.getTeam().getId());
            if (g != null) g.getUsers().add(u);
        }
        groups.values().removeIf(g -> g.getUsers().isEmpty());
        return new ArrayList<>(groups.values());
    }
}
