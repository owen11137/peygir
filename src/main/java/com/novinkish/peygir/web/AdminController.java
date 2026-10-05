package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import com.novinkish.peygir.security.PeygirUser;
import com.novinkish.peygir.service.AdminService;
import com.novinkish.peygir.service.ExcelService;
import com.novinkish.peygir.service.SettingService;
import com.novinkish.peygir.service.UserFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** پنل ادمین: تیم‌ها، کاربران، نقش‌ها و دسترسی‌ها، علت‌ها، تنظیمات و خروجی Excel هر صفحه. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final ExcelService excel;
    private final SettingService settings;
    private final TeamRepository teams;
    private final UserRepository users;
    private final ReasonRepository reasons;
    private final RoleRepository roles;

    private static String yesNo(boolean b, String yes, String no) { return b ? yes : no; }

    private static String permLabels(Collection<Permission> perms) {
        return perms.stream().map(Permission::getLabel).collect(Collectors.joining("، "));
    }

    // ---------------------------------------------------------------- تیم‌ها
    @GetMapping("/teams")
    public String teamsPage(Model model) {
        model.addAttribute("teams", teams.findAllByOrderByName());
        return "admin/teams";
    }

    @PostMapping("/teams")
    public String addTeam(@RequestParam String name, RedirectAttributes ra) {
        admin.createTeam(name);
        ra.addFlashAttribute("success", "تیم اضافه شد");
        return "redirect:/admin/teams";
    }

    @PostMapping("/teams/{id}/rename")
    public String renameTeam(@PathVariable Long id, @RequestParam String name, RedirectAttributes ra) {
        admin.renameTeam(id, name);
        ra.addFlashAttribute("success", "نام تیم ذخیره شد");
        return "redirect:/admin/teams";
    }

    @PostMapping("/teams/{id}/toggle")
    public String toggleTeam(@PathVariable Long id, RedirectAttributes ra) {
        admin.toggleTeam(id);
        ra.addFlashAttribute("success", "وضعیت تیم تغییر کرد");
        return "redirect:/admin/teams";
    }

    @GetMapping("/teams/export")
    public ResponseEntity<byte[]> exportTeams() throws Exception {
        Map<Long, Long> counts = users.findAll().stream()
                .collect(Collectors.groupingBy(u -> u.getTeam().getId(), Collectors.counting()));
        List<Object[]> rows = new ArrayList<>();
        for (Team t : teams.findAllByOrderByName())
            rows.add(new Object[]{t.getName(), yesNo(t.isActive(), "فعال", "غیرفعال"), counts.getOrDefault(t.getId(), 0L)});
        return Xlsx.ok("peygir-teams.xlsx", excel.table("تیم‌ها", new String[]{"تیم", "وضعیت", "تعداد کاربران"}, rows));
    }

    // ---------------------------------------------------------------- کاربران
    @GetMapping("/users")
    public String usersPage(@ModelAttribute("filter") UserFilter filter,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        var userPage = admin.searchUsers(filter, page);
        model.addAttribute("userPage", userPage);
        model.addAttribute("usersByTeam", userPage.getContent().stream()
                .collect(Collectors.groupingBy(u -> u.getTeam().getId())));
        model.addAttribute("teams", teams.findAllByOrderByName());
        model.addAttribute("roles", roles.findAllByOrderByName());
        model.addAttribute("permissions", Permission.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String addUser(@RequestParam String username, @RequestParam String fullName, @RequestParam Long teamId,
                          @RequestParam(name = "roleId", required = false) List<Long> roleIds,
                          @RequestParam String password, RedirectAttributes ra) {
        admin.createUser(username, fullName, teamId, roleIds, password);
        ra.addFlashAttribute("success", "کاربر ساخته شد؛ در اولین ورود باید رمز را عوض کند");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@AuthenticationPrincipal PeygirUser me, @PathVariable Long id, @RequestParam String fullName,
                             @RequestParam Long teamId,
                             @RequestParam(name = "roleId", required = false) List<Long> roleIds,
                             @RequestParam(defaultValue = "false") boolean active,
                             @RequestParam(name = "perm", required = false) List<Permission> perms,
                             @RequestParam(defaultValue = "") String search,
                             @RequestParam(required = false) Long filterTeamId,
                             @RequestParam(defaultValue = "0") int page, RedirectAttributes ra) {
        admin.updateUser(me.getId(), id, fullName, teamId, roleIds, active, perms);
        ra.addFlashAttribute("success", "نقش‌ها و دسترسی‌های کاربر ذخیره شد");
        return usersRedirect(search, filterTeamId, page, ra);
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @RequestParam String password,
                                @RequestParam(defaultValue = "") String search,
                                @RequestParam(required = false) Long filterTeamId,
                                @RequestParam(defaultValue = "0") int page, RedirectAttributes ra) {
        admin.resetPassword(id, password);
        ra.addFlashAttribute("success", "رمز جدید تنظیم شد؛ کاربر در اولین ورود باید آن را عوض کند");
        return usersRedirect(search, filterTeamId, page, ra);
    }

    private static String usersRedirect(String search, Long teamId, int page, RedirectAttributes ra) {
        if (!search.isBlank()) ra.addAttribute("search", search);
        if (teamId != null) ra.addAttribute("teamId", teamId);
        if (page > 0) ra.addAttribute("page", page);
        return "redirect:/admin/users#usersList";
    }

    @PostMapping("/users/import")
    public String importUsers(@RequestParam("file") MultipartFile file, RedirectAttributes ra) throws Exception {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "فایل Excel را انتخاب کنید");
            return "redirect:/admin/users";
        }
        var res = admin.importUsers(file.getInputStream());
        ra.addFlashAttribute("success", res.created() + " کاربر ساخته شد، " + res.skipped() + " ردیف رد شد");
        if (!res.errors().isEmpty()) ra.addFlashAttribute("importErrors", res.errors());
        return "redirect:/admin/users";
    }

    @GetMapping("/users/template")
    public ResponseEntity<byte[]> template() throws Exception {
        return Xlsx.ok("peygir-users-template.xlsx", excel.usersTemplate());
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (AppUser u : users.findAllByOrderByFullName())
            rows.add(new Object[]{u.getFullName(), u.getUsername(), u.getTeam().getName(), u.getRoleNames(),
                    yesNo(u.isActive(), "فعال", "غیرفعال"), permLabels(u.effectivePermissions()),
                    yesNo(u.hasCustomPermissions(), "بله", "خیر")});
        return Xlsx.ok("peygir-users.xlsx", excel.table("کاربران",
                new String[]{"نام", "نام کاربری", "تیم", "نقش‌ها", "وضعیت", "دسترسی‌های نهایی", "دسترسی سفارشی"}, rows));
    }

    // ---------------------------------------------------------------- نقش‌ها
    @GetMapping("/roles")
    public String rolesPage(Model model) {
        List<AppRole> all = roles.findAllByOrderByName();
        Map<Long, Long> counts = new HashMap<>();
        for (AppRole r : all) counts.put(r.getId(), users.countByRolesId(r.getId()));
        model.addAttribute("roles", all);
        model.addAttribute("counts", counts);
        model.addAttribute("permissions", Permission.values());
        return "admin/roles";
    }

    @PostMapping("/roles")
    public String saveRole(@RequestParam(required = false) Long id, @RequestParam String name,
                           @RequestParam(required = false) String description,
                           @RequestParam(name = "perm", required = false) List<Permission> perms, RedirectAttributes ra) {
        admin.saveRole(id, name, description, perms);
        ra.addFlashAttribute("success", id == null ? "نقش ساخته شد" : "نقش ذخیره شد");
        return "redirect:/admin/roles";
    }

    @PostMapping("/roles/{id}/delete")
    public String deleteRole(@PathVariable Long id, RedirectAttributes ra) {
        admin.deleteRole(id);
        ra.addFlashAttribute("success", "نقش حذف شد");
        return "redirect:/admin/roles";
    }

    @GetMapping("/roles/export")
    public ResponseEntity<byte[]> exportRoles() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (AppRole r : roles.findAllByOrderByName())
            rows.add(new Object[]{r.getName(), r.getDescription() == null ? "" : r.getDescription(),
                    yesNo(r.isSystemRole(), "بله", "خیر"), permLabels(r.getPermissions()), users.countByRolesId(r.getId())});
        return Xlsx.ok("peygir-roles.xlsx", excel.table("نقش‌ها",
                new String[]{"نقش", "توضیح", "پیش‌فرض سیستم", "دسترسی‌ها", "تعداد کاربران"}, rows));
    }

    // ---------------------------------------------------------------- علت‌ها
    @GetMapping("/reasons")
    public String reasonsPage(Model model) {
        model.addAttribute("reasons", reasons.findAllByOrderBySortOrder());
        return "admin/reasons";
    }

    @PostMapping("/reasons")
    public String saveReason(@RequestParam(required = false) Long id, @RequestParam String title,
                             @RequestParam(defaultValue = "0") int sortOrder,
                             @RequestParam(defaultValue = "false") boolean requiresDetail,
                             @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        admin.saveReason(id, title, sortOrder, requiresDetail, active);
        ra.addFlashAttribute("success", "علت ذخیره شد");
        return "redirect:/admin/reasons";
    }

    @GetMapping("/reasons/export")
    public ResponseEntity<byte[]> exportReasons() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (Reason r : reasons.findAllByOrderBySortOrder())
            rows.add(new Object[]{r.getTitle(), r.getSortOrder(), yesNo(r.isRequiresDetail(), "بله", "خیر"),
                    yesNo(r.isActive(), "فعال", "غیرفعال")});
        return Xlsx.ok("peygir-reasons.xlsx", excel.table("علت‌ها",
                new String[]{"علت", "ترتیب", "توضیح اجباری", "وضعیت"}, rows));
    }

    // ---------------------------------------------------------------- تنظیمات
    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("refreshSeconds", settings.dashboardRefreshSeconds());
        model.addAttribute("refreshMin", SettingService.REFRESH_MIN);
        model.addAttribute("refreshMax", SettingService.REFRESH_MAX);
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam int refreshSeconds, RedirectAttributes ra) {
        settings.setDashboardRefreshSeconds(refreshSeconds);
        ra.addFlashAttribute("success", "تنظیمات ذخیره شد");
        return "redirect:/admin/settings";
    }
}
