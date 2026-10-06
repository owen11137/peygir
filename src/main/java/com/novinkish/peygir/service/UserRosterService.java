package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.AppRole;
import com.novinkish.peygir.domain.AppUser;
import com.novinkish.peygir.domain.Permission;
import com.novinkish.peygir.domain.Team;
import com.novinkish.peygir.repository.RoleRepository;
import com.novinkish.peygir.repository.TeamRepository;
import com.novinkish.peygir.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Replaces the active roster without deleting identities referenced by report history. */
@Service
@RequiredArgsConstructor
public class UserRosterService {
    private final UserRepository users;
    private final TeamRepository teams;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    @Value("${peygir.default-password:Peygir@123}")
    private String defaultPassword;

    public record ReplacementResult(int created, int updated, int deactivated, List<String> errors) {}

    private record Entry(int line, String team, String name, String username, String oldUsername,
                         Set<AppRole> roles, String password, Set<String> supervisedTeams,
                         boolean hasSupervisionColumn, AppUser existing, boolean protectedAccount) {}

    /**
     * Validates the complete first sheet before making any change. Username/old username are the
     * only identity keys: names and phone numbers must never merge two people's report histories.
     */
    @Transactional
    public ReplacementResult replaceUsers(InputStream in, Long actorId, boolean mustChangePassword) {
        List<AppUser> originalUsers = users.findAll();
        AppUser actor = originalUsers.stream().filter(u -> Objects.equals(u.getId(), actorId)).findFirst()
                .orElseThrow(() -> new BusinessException("مدیر سیستم واردشده پیدا نشد"));
        if (!actor.isActive() || !actor.getTeam().isActive() || !actor.has(Permission.ADMIN_PANEL))
            throw new BusinessException("جایگزینی فهرست کاربران فقط برای مدیر سیستم فعال مجاز است");

        Set<Long> protectedIds = new HashSet<>();
        protectedIds.add(actorId);
        Map<String, AppUser> existingByUsername = new HashMap<>();
        for (AppUser user : originalUsers) {
            if (user.has(Permission.ADMIN_PANEL)) protectedIds.add(user.getId());
            if (existingByUsername.put(username(user.getUsername()), user) != null)
                throw new BusinessException("نام‌های کاربری موجود فقط در بزرگی و کوچکی حروف تفاوت دارند؛ ابتدا آن‌ها را اصلاح کنید");
        }

        List<String> errors = new ArrayList<>();
        List<Entry> entries = readEntries(in, existingByUsername, protectedIds, errors);
        if (!errors.isEmpty()) return new ReplacementResult(0, 0, 0, List.copyOf(errors));

        // Resolve teams and encode passwords only after the complete workbook has passed validation.
        Map<String, Team> teamByName = new HashMap<>();
        teams.findAll().forEach(t -> teamByName.put(t.getName(), t));
        Set<Long> retainedIds = new HashSet<>(protectedIds);
        int created = 0, updated = 0, deactivated = 0;
        for (Entry entry : entries) {
            if (entry.protectedAccount()) continue;
            AppUser user = entry.existing();
            boolean isNew = user == null;
            if (isNew) user = new AppUser();
            user.setUsername(entry.username());
            user.setFullName(entry.name());
            user.setTeam(resolveTeam(teamByName, entry.team()));
            user.setActive(true);
            user.getRoles().clear();
            user.getRoles().addAll(entry.roles());
            if (entry.hasSupervisionColumn()) {
                user.getSupervisedTeams().clear();
                for (String name : entry.supervisedTeams()) user.getSupervisedTeams().add(resolveTeam(teamByName, name));
            }
            if (isNew || !entry.password().isEmpty()) {
                user.setPasswordHash(encoder.encode(entry.password().isEmpty() ? defaultPassword : entry.password()));
                user.setMustChangePassword(mustChangePassword);
            }
            users.save(user);
            retainedIds.add(user.getId());
            if (isNew) created++; else updated++;
        }
        for (AppUser user : originalUsers) {
            if (!retainedIds.contains(user.getId()) && user.isActive()) {
                user.setActive(false);
                deactivated++;
            }
        }
        users.flush();
        return new ReplacementResult(created, updated, deactivated, List.of());
    }

    private List<Entry> readEntries(InputStream in, Map<String, AppUser> existingByUsername,
                                    Set<Long> protectedIds, List<String> errors) {
        List<Entry> entries = new ArrayList<>();
        Map<String, AppRole> roleByKey = roleLookup();
        Set<String> newUsernames = new HashSet<>(), oldUsernames = new HashSet<>();
        Set<Long> mappedIds = new HashSet<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException("فایل Excel شیت کاربران ندارد");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            validateHeader(header, formatter);
            int oldUsernameColumn = optionalColumn(header, formatter, "نام کاربری قبلی");
            int supervisionColumn = optionalColumn(header, formatter, "تیم‌های تحت نظارت");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlank(row, formatter)) continue;
                int line = i + 1;
                int before = errors.size();
                String team = cell(row, 0, formatter), name = cell(row, 1, formatter);
                String newUsername = username(cell(row, 2, formatter));
                String roleText = cell(row, 3, formatter), password = cell(row, 4, formatter);
                String oldUsername = username(cell(row, oldUsernameColumn, formatter));
                Set<String> supervision = new LinkedHashSet<>();
                if (supervisionColumn >= 0) {
                    for (String part : cell(row, supervisionColumn, formatter).split("[|؛]")) {
                        if (!part.isBlank()) supervision.add(part.trim());
                    }
                }
                required(errors, line, team, "تیم", 120);
                required(errors, line, name, "نام", 150);
                required(errors, line, newUsername, "نام کاربری", 80);
                maxLength(errors, line, oldUsername, "نام کاربری قبلی", 80);
                supervision.forEach(t -> maxLength(errors, line, t, "تیم تحت نظارت", 120));
                if (!newUsername.isEmpty() && !newUsernames.add(newUsername))
                    error(errors, line, "نام کاربری «" + newUsername + "» در فایل تکراری است");
                if (!oldUsername.isEmpty() && !oldUsernames.add(oldUsername))
                    error(errors, line, "نام کاربری قبلی «" + oldUsername + "» در فایل تکراری است");

                Set<AppRole> selectedRoles = new HashSet<>();
                for (String part : roleText.split("[،,;؛|]")) {
                    if (part.isBlank()) continue;
                    AppRole role = roleByKey.get(norm(part));
                    if (role == null) error(errors, line, "نقش «" + part.trim() + "» معتبر نیست");
                    else selectedRoles.add(role);
                }
                if (selectedRoles.isEmpty()) error(errors, line, "حداقل یک نقش معتبر را مشخص کنید");

                AppUser oldUser = existingByUsername.get(oldUsername), newUser = existingByUsername.get(newUsername);
                if (oldUser != null && newUser != null && !oldUser.getId().equals(newUser.getId()))
                    error(errors, line, "نام کاربری جدید و قبلی به دو شخص متفاوت تعلق دارند؛ جابه‌جایی نام کاربری مجاز نیست");
                AppUser existing = oldUser != null ? oldUser : newUser;
                if (existing != null && !mappedIds.add(existing.getId()))
                    error(errors, line, "این کاربر موجود بیش از یک بار در فایل آمده است");
                if (!password.isEmpty()) validatePassword(errors, line, password);
                else if (existing == null) validatePassword(errors, line, defaultPassword);
                boolean protectedAccount = existing != null && protectedIds.contains(existing.getId());
                if (protectedAccount && wouldChangeProtected(existing, team, name, newUsername,
                        selectedRoles, password, supervision, supervisionColumn >= 0))
                    error(errors, line, "حساب مدیر سیستم از جایگزینی فهرست مستثنا است؛ ردیف آن را حذف کنید یا بدون تغییر نگه دارید");
                if (errors.size() == before) entries.add(new Entry(line, team, name, newUsername, oldUsername,
                        selectedRoles, password, supervision, supervisionColumn >= 0, existing, protectedAccount));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("فایل Excel خوانده نشد؛ از فایل سالم با قالب کاربران استفاده کنید");
        }
        if (entries.isEmpty() && errors.isEmpty())
            throw new BusinessException("فایل فهرست کاربران خالی است؛ هیچ کاربری تغییر نکرد");
        if (!entries.isEmpty() && errors.isEmpty() && entries.stream().allMatch(Entry::protectedAccount))
            throw new BusinessException("فایل فقط شامل مدیران سیستم است؛ برای جایگزینی، فهرست کاربران را وارد کنید");
        return entries;
    }

    private static boolean wouldChangeProtected(AppUser user, String team, String name, String username,
                                                Set<AppRole> selectedRoles, String password,
                                                Set<String> supervision, boolean hasSupervisionColumn) {
        Set<Long> originalRoles = new HashSet<>(), newRoles = new HashSet<>();
        user.getRoles().forEach(r -> originalRoles.add(r.getId()));
        selectedRoles.forEach(r -> newRoles.add(r.getId()));
        Set<String> originalSupervision = new HashSet<>();
        user.getSupervisedTeams().forEach(t -> originalSupervision.add(t.getName()));
        return !user.getUsername().equals(username) || !user.getFullName().equals(name)
                || !user.getTeam().getName().equals(team) || !originalRoles.equals(newRoles)
                || !password.isEmpty() || (hasSupervisionColumn && !originalSupervision.equals(supervision));
    }

    private Team resolveTeam(Map<String, Team> byName, String name) {
        return byName.computeIfAbsent(name, n -> teams.save(new Team(n)));
    }

    private Map<String, AppRole> roleLookup() {
        Map<String, AppRole> byKey = new HashMap<>();
        Map<String, AppRole> byCode = new HashMap<>();
        for (AppRole role : roles.findAll()) {
            byKey.put(norm(role.getName()), role);
            if (role.getCode() != null) {
                byKey.put(norm(role.getCode()), role);
                byCode.put(role.getCode(), role);
            }
        }
        alias(byKey, byCode, "عضو", "USER"); alias(byKey, byCode, "کاربر", "USER");
        alias(byKey, byCode, "مدیر", "TEAM_MANAGER");
        alias(byKey, byCode, "مدیرعامل", "SENIOR_MANAGER");
        alias(byKey, byCode, "مدیریت ارشد", "SENIOR_MANAGER");
        return byKey;
    }

    private static void alias(Map<String, AppRole> byKey, Map<String, AppRole> byCode, String name, String code) {
        if (byCode.containsKey(code)) byKey.putIfAbsent(norm(name), byCode.get(code));
    }

    private static void validateHeader(Row header, DataFormatter formatter) {
        List<Set<String>> titles = List.of(Set.of("تیم", "نام تیم", "team"),
                Set.of("نام", "نام و نام خانوادگی", "نام کامل", "fullname", "full name"),
                Set.of("نام کاربری", "نامکاربری", "username", "نام کاربری جدید"),
                Set.of("نقش", "نقش ها", "نقش (چند نقش با ویرگول)", "role", "roles"),
                Set.of("رمز", "رمز اولیه", "رمز (اختیاری)", "رمز اولیه (اختیاری)", "password"));
        if (header == null) throw new BusinessException("ردیف عنوان ستون‌ها در فایل وجود ندارد");
        for (int i = 0; i < titles.size(); i++) {
            if (!titles.get(i).contains(norm(cell(header, i, formatter))))
                throw new BusinessException("پنج ستون اول فایل باید به‌ترتیب تیم، نام، نام کاربری، نقش و رمز باشند؛ خانهٔ رمز می‌تواند خالی باشد");
        }
    }

    private static int optionalColumn(Row header, DataFormatter formatter, String title) {
        int found = -1;
        for (int i = 5; i < header.getLastCellNum(); i++) {
            if (title.equals(cell(header, i, formatter))) {
                if (found >= 0) throw new BusinessException("عنوان ستون «" + title + "» در فایل تکراری است");
                found = i;
            }
        }
        return found;
    }

    private static boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) if (!cell(row, i, formatter).isEmpty()) return false;
        return true;
    }

    private static String cell(Row row, int column, DataFormatter formatter) {
        return row == null || column < 0 ? "" : formatter.formatCellValue(row.getCell(column)).trim();
    }

    private static void required(List<String> errors, int line, String value, String label, int max) {
        if (value.isEmpty()) error(errors, line, label + " الزامی است");
        maxLength(errors, line, value, label, max);
    }

    private static void maxLength(List<String> errors, int line, String value, String label, int max) {
        if (value.length() > max) error(errors, line, label + " نباید بیش از " + max + " نویسه باشد");
    }

    private static void validatePassword(List<String> errors, int line, String password) {
        if (password == null || password.length() < 6) error(errors, line, "رمز باید حداقل ۶ نویسه باشد");
        else if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            error(errors, line, "رمز برای ذخیره‌سازی امن بیش از حد طولانی است (حداکثر ۷۲ بایت)");
    }

    private static void error(List<String> errors, int line, String message) {
        errors.add("ردیف " + line + ": " + message);
    }

    private static String username(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private static String norm(String value) {
        return value.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ').trim()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
