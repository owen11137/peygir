package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.ReasonRepository;
import com.novinkish.peygir.repository.ReportRepository;
import com.novinkish.peygir.repository.RoleRepository;
import com.novinkish.peygir.repository.TeamRepository;
import com.novinkish.peygir.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

/** مدیریت داده‌های پایه توسط ادمین: تیم‌ها، کاربران، نقش‌ها، دسترسی‌ها، علت‌ها و ورود گروهی. */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final TeamRepository teams;
    private final ReportRepository reports;
    private final UserRepository users;
    private final ReasonRepository reasons;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    @Value("${peygir.default-password:Peygir@123}")
    private String defaultPassword;

    public record ImportResult(int created, int skipped, List<String> errors) {}

    // ---------------------------------------------------------------- تیم‌ها
    @Transactional
    public void createTeam(String name) {
        name = required(name, "نام تیم را وارد کنید");
        if (teams.findByName(name).isPresent()) throw new BusinessException("تیمی با این نام وجود دارد");
        teams.save(new Team(name));
    }

    @Transactional
    public void renameTeam(Long id, String name) {
        name = required(name, "نام تیم را وارد کنید");
        Team t = teams.findById(id).orElseThrow();
        var other = teams.findByName(name);
        if (other.isPresent() && !other.get().getId().equals(id)) throw new BusinessException("تیمی با این نام وجود دارد");
        t.setName(name);
    }

    @Transactional
    public void toggleTeam(Long id) {
        Team t = teams.findById(id).orElseThrow();
        t.setActive(!t.isActive());
        teams.saveAndFlush(t);
        ensureAdminExists();
    }

    /** تیم دارای گزارش (در هر وضعیت) یا عضو، قابل حذف نیست. */
    @Transactional
    public void deleteTeam(Long id) {
        Team team = teams.findById(id).orElseThrow(() -> new BusinessException("تیم موردنظر پیدا نشد"));
        if (reports.existsByCallerTeamIdOrTargetTeamId(id, id))
            throw new BusinessException("این تیم گزارش یا پیگیری ثبت‌شده دارد و قابل حذف نیست؛ می‌توانید آن را غیرفعال کنید");
        if (users.existsByTeamId(id))
            throw new BusinessException("این تیم کاربر دارد؛ ابتدا کاربران آن را از بخش «کاربران» به تیم دیگری منتقل کنید، سپس تیم را حذف کنید");
        teams.delete(team);
        teams.flush();
    }

    // ---------------------------------------------------------------- کاربران
    @Transactional(readOnly = true)
    public Page<AppUser> searchUsers(UserFilter filter, int page) {
        int pageSize = 25;
        int pageNumber = Math.max(0, Math.min(page, Integer.MAX_VALUE / pageSize));
        var spec = filter.toSpec();
        Sort sort = Sort.by("team.name", "fullName", "id");
        Page<AppUser> result = users.findAll(spec, PageRequest.of(pageNumber, pageSize, sort));
        if (pageNumber > 0 && result.isEmpty()) {
            int lastPage = Math.max(0, result.getTotalPages() - 1);
            result = users.findAll(spec, PageRequest.of(lastPage, pageSize, sort));
        }
        return result;
    }

    @Transactional
    public void createUser(String username, String fullName, Long teamId, Collection<Long> roleIds, String password, boolean mustChangePassword) {
        username = required(username, "نام کاربری را وارد کنید").toLowerCase();
        fullName = required(fullName, "نام و نام خانوادگی را وارد کنید");
        if (users.existsByUsername(username)) throw new BusinessException("این نام کاربری قبلاً ثبت شده است");
        checkPassword(password);
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setTeam(teams.findById(teamId).orElseThrow(() -> new BusinessException("تیم را انتخاب کنید")));
        u.getRoles().addAll(resolveRoles(roleIds));
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(mustChangePassword);
        users.save(u);
    }

    /**
     * @param roleIds   نقش‌های کاربر (چندتایی؛ می‌شود همه را برداشت)
     * @param effective دسترسی‌های نهایی (تیک‌های صفحه). تفاوتش با مجموع نقش‌ها به‌صورت
     *                  «اعطاشده» و «سلب‌شده» برای همان کاربر ذخیره می‌شود.
     */
    @Transactional
    public void updateUser(Long actorId, Long id, String fullName, Long teamId, Collection<Long> roleIds,
                           boolean active, Collection<Permission> effective, boolean mustChangePassword) {
        AppUser u = users.findById(id).orElseThrow();
        Set<AppRole> newRoles = resolveRoles(roleIds);
        Set<Permission> base = EnumSet.noneOf(Permission.class);
        newRoles.forEach(r -> base.addAll(r.getPermissions()));
        Set<Permission> eff = EnumSet.noneOf(Permission.class);
        if (effective != null) eff.addAll(effective);
        if (id.equals(actorId) && (!active || !eff.contains(Permission.ADMIN_PANEL)))
            throw new BusinessException("نمی‌توانید خودتان را غیرفعال کنید یا دسترسی «مدیریت سیستم» را از خودتان بگیرید");

        u.setFullName(required(fullName, "نام را وارد کنید"));
        u.setTeam(teams.findById(teamId).orElseThrow(() -> new BusinessException("تیم را انتخاب کنید")));
        u.setActive(active);
        u.setMustChangePassword(mustChangePassword);
        u.getRoles().clear();
        u.getRoles().addAll(newRoles);

        Set<Permission> grants = EnumSet.noneOf(Permission.class);
        grants.addAll(eff);
        grants.removeAll(base);
        Set<Permission> revokes = EnumSet.noneOf(Permission.class);
        revokes.addAll(base);
        revokes.removeAll(eff);
        u.getGrantedPermissions().clear();
        u.getGrantedPermissions().addAll(grants);
        u.getRevokedPermissions().clear();
        u.getRevokedPermissions().addAll(revokes);
        users.saveAndFlush(u);
        ensureAdminExists();
    }

    @Transactional
    public void resetPassword(Long id, String password, boolean mustChangePassword) {
        checkPassword(password);
        AppUser u = users.findById(id).orElseThrow();
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(mustChangePassword);
    }

    // ---------------------------------------------------------------- نقش‌ها
    /** ساخت یا ویرایش نقش و دسترسی‌هایش. */
    @Transactional
    public void saveRole(Long id, String name, String description, Collection<Permission> perms) {
        name = required(name, "نام نقش را وارد کنید");
        AppRole r = id == null ? new AppRole() : roles.findById(id).orElseThrow();
        var other = roles.findByName(name);
        if (other.isPresent() && !other.get().getId().equals(r.getId())) throw new BusinessException("نقشی با این نام وجود دارد");
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        if (perms != null) set.addAll(perms);
        if ("ADMIN".equals(r.getCode()) && !set.contains(Permission.ADMIN_PANEL))
            throw new BusinessException("نقش «ادمین» باید همیشه دسترسی «مدیریت سیستم» را داشته باشد");
        r.setName(name);
        r.setDescription(clean(description));
        r.getPermissions().clear();
        r.getPermissions().addAll(set);
        roles.saveAndFlush(r);
        ensureAdminExists();
    }

    @Transactional
    public void deleteRole(Long id) {
        AppRole r = roles.findById(id).orElseThrow();
        if (r.isSystemRole()) throw new BusinessException("نقش‌های پیش‌فرض سیستم حذف نمی‌شوند (ولی دسترسی‌هایشان قابل ویرایش است)");
        long n = users.countByRolesId(id);
        if (n > 0) throw new BusinessException("این نقش به " + n + " کاربر داده شده است؛ ابتدا از آن‌ها بگیرید");
        roles.delete(r);
    }

    // ---------------------------------------------------------------- علت‌ها
    @Transactional
    public void saveReason(Long id, String title, int sortOrder, boolean requiresDetail, boolean active) {
        title = required(title, "عنوان علت را وارد کنید");
        Reason r = id == null ? new Reason() : reasons.findById(id).orElseThrow();
        r.setTitle(title);
        r.setSortOrder(sortOrder);
        r.setRequiresDetail(requiresDetail);
        r.setActive(active);
        reasons.save(r);
    }

    // ---------------------------------------------------------------- ورود گروهی از Excel
    /** ستون‌ها: تیم | نام | نام‌کاربری | نقش (یک یا چند نقش با ویرگول) | رمز (اختیاری). */
    @Transactional
    public ImportResult importUsers(InputStream in, boolean mustChangePassword) throws Exception {
        List<String> errors = new ArrayList<>();
        int created = 0, skipped = 0;
        Map<String, AppRole> byKey = roleLookup();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            for (int i = 1; i <= sh.getLastRowNum(); i++) {
                Row row = sh.getRow(i);
                if (row == null) continue;
                String team = fmt.formatCellValue(row.getCell(0)).trim();
                String name = fmt.formatCellValue(row.getCell(1)).trim();
                String username = fmt.formatCellValue(row.getCell(2)).trim().toLowerCase();
                String roleText = fmt.formatCellValue(row.getCell(3)).trim();
                String pass = fmt.formatCellValue(row.getCell(4)).trim();
                if (team.isEmpty() && name.isEmpty() && username.isEmpty()) continue;
                int line = i + 1;
                if (team.isEmpty() || name.isEmpty() || username.isEmpty()) {
                    errors.add("ردیف " + line + ": تیم، نام و نام کاربری الزامی است");
                    skipped++;
                    continue;
                }
                Set<AppRole> userRoles = new HashSet<>();
                String badRole = null;
                for (String part : roleText.split("[،,;؛|]")) {
                    if (part.isBlank()) continue;
                    AppRole r = byKey.get(norm(part));
                    if (r == null) { badRole = part.trim(); break; }
                    userRoles.add(r);
                }
                if (badRole != null || userRoles.isEmpty()) {
                    errors.add("ردیف " + line + ": نقش «" + (badRole != null ? badRole : roleText) + "» معتبر نیست");
                    skipped++;
                    continue;
                }
                if (users.existsByUsername(username)) {
                    errors.add("ردیف " + line + ": نام کاربری «" + username + "» از قبل وجود دارد");
                    skipped++;
                    continue;
                }
                if (!pass.isEmpty() && pass.length() < 6) {
                    errors.add("ردیف " + line + ": رمز باید حداقل ۶ نویسه باشد");
                    skipped++;
                    continue;
                }
                Team t = teams.findByName(team).orElseGet(() -> teams.save(new Team(team)));
                AppUser u = new AppUser();
                u.setUsername(username);
                u.setFullName(name);
                u.setTeam(t);
                u.getRoles().addAll(userRoles);
                u.setPasswordHash(encoder.encode(pass.isEmpty() ? defaultPassword : pass));
                u.setMustChangePassword(mustChangePassword);
                users.save(u);
                created++;
            }
        }
        return new ImportResult(created, skipped, errors);
    }

    // ---------------------------------------------------------------- کمکی
    private Map<String, AppRole> roleLookup() {
        Map<String, AppRole> m = new HashMap<>();
        for (AppRole r : roles.findAll()) {
            m.put(norm(r.getName()), r);
            if (r.getCode() != null) m.put(norm(r.getCode()), r);
        }
        // عنوان‌های کوتاه برای نقش‌های پیش‌فرض
        alias(m, "عضو", "USER"); alias(m, "کاربر", "USER");
        alias(m, "مدیر", "TEAM_MANAGER");
        alias(m, "مدیرعامل", "SENIOR_MANAGER"); alias(m, "مدیریت ارشد", "SENIOR_MANAGER");
        return m;
    }

    private void alias(Map<String, AppRole> m, String text, String code) {
        roles.findByCode(code).ifPresent(r -> m.putIfAbsent(norm(text), r));
    }

    private static String norm(String s) {
        return s.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ').trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private Set<AppRole> resolveRoles(Collection<Long> ids) {
        Set<AppRole> set = new HashSet<>();
        if (ids != null && !ids.isEmpty()) set.addAll(roles.findAllById(ids));
        return set;
    }

    /** بعد از هر تغییر حساس، باید دست‌کم یک کاربر فعال با دسترسی «مدیریت سیستم» بماند. */
    private void ensureAdminExists() {
        boolean ok = users.findAll().stream().anyMatch(x -> x.isActive() && x.getTeam().isActive() && x.has(Permission.ADMIN_PANEL));
        if (!ok) throw new BusinessException("حداقل یک کاربر فعال با دسترسی «مدیریت سیستم» باید باقی بماند");
    }

    private void checkPassword(String p) {
        if (p == null || p.length() < 6) throw new BusinessException("رمز باید حداقل ۶ نویسه باشد");
    }

    private static String clean(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static String required(String s, String msg) {
        if (s == null || s.isBlank()) throw new BusinessException(msg);
        return s.trim();
    }
}
