package com.novinkish.peygir.config;

import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import com.novinkish.peygir.service.JalaliCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * در اولین اجرا (وقتی هیچ کاربری وجود ندارد):
 *  - حساب ادمین پیش‌فرض را می‌سازد (admin / Admin@123)
 *  - اگر peygir.seed-demo=true باشد، تیم‌ها، کاربران و گزارش‌های نمونه‌ی دمو را هم می‌سازد.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final TeamRepository teams;
    private final UserRepository users;
    private final ReasonRepository reasons;
    private final ReportRepository reports;
    private final ReportEventRepository events;
    private final RoleRepository roleRepo;
    private final PasswordEncoder encoder;

    @Value("${peygir.seed-demo:true}")
    private boolean seedDemo;

    private static final String DEMO_PASSWORD = "Demo@123";

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;

        Team mgmt = teams.save(new Team("مدیریت"));
        AppRole rUser = role("USER"), rManager = role("TEAM_MANAGER"), rSenior = role("SENIOR_MANAGER"), rAdmin = role("ADMIN");
        AppUser admin = user("admin", "مدیر سیستم", mgmt, rAdmin, "Admin@123", false);
        log.info("Default admin created: admin / Admin@123");
        if (!seedDemo) return;

        user("ceo", "مدیرعامل (نمونه)", mgmt, rSenior, DEMO_PASSWORD, false);

        String[][] defs = {
                {"امنیت", "sec", "رضا کریمی", "نگار موسوی", "امیر حسینی"},
                {"توسعه", "dev", "مریم صادقی", "کیان رحیمی", "الهام نوری"},
                {"عملیات", "ops", "حسن جعفری", "پریسا قاسمی", "بهرام یزدانی"},
                {"شبکه", "net", "سعید اکبری", "ندا فرهادی", "پیمان کاظمی"},
                {"پشتیبانی", "sup", "لیلا مرادی", "آرش سلطانی", "شیما عباسی"}};
        Map<String, Team> teamMap = new LinkedHashMap<>();
        Map<String, AppUser> manager = new HashMap<>();
        Map<String, List<AppUser>> members = new HashMap<>();
        for (String[] d : defs) {
            Team t = teams.save(new Team(d[0]));
            teamMap.put(d[0], t);
            manager.put(d[0], user(d[1] + ".mgr", d[2], t, rManager, DEMO_PASSWORD, false));
            List<AppUser> list = new ArrayList<>();
            list.add(manager.get(d[0]));
            list.add(user(d[1] + ".u1", d[3], t, rUser, DEMO_PASSWORD, false));
            list.add(user(d[1] + ".u2", d[4], t, rUser, DEMO_PASSWORD, false));
            members.put(d[0], list);
        }

        // وزن تیم‌های هدف: عملیات و شبکه «کندتر» هستند تا داشبورد دمو معنی‌دار باشد
        List<String> weighted = new ArrayList<>();
        Map<String, Integer> w = Map.of("امنیت", 1, "توسعه", 1, "عملیات", 4, "شبکه", 3, "پشتیبانی", 2);
        w.forEach((k, v) -> { for (int i = 0; i < v; i++) weighted.add(k); });

        List<Reason> rs = reasons.findByActiveTrueOrderBySortOrder();
        List<Reason> reasonPool = new ArrayList<>();
        for (int i = 0; i < rs.size(); i++) {
            int weight = i < 2 ? 4 : (i == 2 ? 3 : 1);
            for (int k = 0; k < weight; k++) reasonPool.add(rs.get(i));
        }
        String[] subjects = {"درخواست دسترسی به سرور", "هماهنگی استقرار نسخه جدید", "اعلام قطعی سرویس", "بررسی هشدار امنیتی",
                "پیگیری تیکت باز", "تغییر قانون فایروال", "ریست رمز سرویس", "درخواست بازیابی بکاپ", "هماهنگی پنجره‌ی نگهداری"};
        ContactMethod[] methods = ContactMethod.values();

        Random rnd = new Random(7);
        List<String> teamNames = new ArrayList<>(teamMap.keySet());
        LocalDateTime now = JalaliCalendar.now();
        for (int i = 0; i < 45; i++) {
            String ct = teamNames.get(rnd.nextInt(teamNames.size()));
            String tt;
            do { tt = weighted.get(rnd.nextInt(weighted.size())); } while (tt.equals(ct));
            List<AppUser> pool = members.get(ct);
            AppUser caller = pool.get(rnd.nextInt(pool.size()));
            LocalDateTime contact = now.minusDays(rnd.nextInt(56)).withHour(8 + rnd.nextInt(10)).withMinute(rnd.nextInt(60));
            if (contact.isAfter(now)) contact = now.minusHours(2);

            Status status;
            int p = rnd.nextInt(100);
            if (caller.has(Permission.TEAM_APPROVE)) status = p < 60 ? Status.APPROVED : (p < 85 ? Status.IN_REVIEW : Status.CLOSED);
            else if (p < 5) status = Status.DRAFT;
            else if (p < 18) status = Status.PENDING;
            else if (p < 23) status = Status.NEEDS_FIX;
            else if (p < 28) status = Status.REJECTED;
            else if (p < 70) status = Status.APPROVED;
            else if (p < 88) status = Status.IN_REVIEW;
            else status = Status.CLOSED;

            Report r = new Report();
            r.setCaller(caller);
            r.setCallerTeam(caller.getTeam());
            r.setTargetTeam(teamMap.get(tt));
            AppUser targetUser = rnd.nextBoolean() ? members.get(tt).get(1 + rnd.nextInt(2)) : null;
            r.setTargetUser(targetUser);
            r.setTargetPerson(targetUser == null ? null : targetUser.getFullName());
            r.setContactMethod(methods[rnd.nextInt(methods.length)]);
            r.setContactAt(contact);
            r.setAttemptsCount(1 + rnd.nextInt(4));
            r.setSubject(subjects[rnd.nextInt(subjects.length)]);
            Reason reason = reasonPool.get(rnd.nextInt(reasonPool.size()));
            r.setReason(reason);
            r.setReasonDetail(reason.isRequiresDetail() ? "توضیح تکمیلی نمونه" : null);
            r.setStatus(status);
            r.setSubmittedByManager(caller.has(Permission.TEAM_APPROVE) && status != Status.DRAFT);
            LocalDateTime created = contact.plusMinutes(20);
            if (created.isAfter(now)) created = now;
            r.setCreatedAt(created);
            r.setUpdatedAt(created);
            r = reports.save(r);
            r.setTrackingNo(String.format("PG-%05d", r.getId()));

            AppUser mgr = manager.get(ct.equals(r.getCallerTeam().getName()) ? ct : r.getCallerTeam().getName());
            LocalDateTime t0 = created;
            ev(r, caller, EventType.CREATED, null, Status.DRAFT, null, t0);
            if (status != Status.DRAFT) {
                if (r.isSubmittedByManager()) {
                    ev(r, caller, EventType.SUBMITTED_BY_MANAGER, Status.DRAFT, Status.APPROVED, null, t0.plusMinutes(5));
                } else {
                    ev(r, caller, EventType.SUBMITTED, Status.DRAFT, Status.PENDING, null, t0.plusMinutes(5));
                    if (status == Status.NEEDS_FIX) ev(r, mgr, EventType.RETURNED, Status.PENDING, Status.NEEDS_FIX, "لطفاً تاریخ و موضوع تماس را دقیق‌تر بنویسید", t0.plusHours(3));
                    else if (status == Status.REJECTED) ev(r, mgr, EventType.REJECTED, Status.PENDING, Status.REJECTED, "این مورد تکراری است", t0.plusHours(3));
                    else if (status.isValidated()) ev(r, mgr, EventType.APPROVED, Status.PENDING, Status.APPROVED, null, t0.plusHours(3));
                }
                if (status == Status.IN_REVIEW || status == Status.CLOSED)
                    ev(r, users.findByUsername("ceo").orElseThrow(), EventType.IN_REVIEW, Status.APPROVED, Status.IN_REVIEW, null, t0.plusDays(1));
                if (status == Status.CLOSED)
                    ev(r, users.findByUsername("ceo").orElseThrow(), EventType.CLOSED, Status.IN_REVIEW, Status.CLOSED, "پیگیری شد و موضوع با مدیر تیم هدف مطرح شد", t0.plusDays(2));
            }
            reports.save(r);
        }
        log.info("Demo data created. Demo accounts: ceo, sec.mgr, sec.u1 ... (password {})", DEMO_PASSWORD);
    }

    private AppRole role(String code) {
        return roleRepo.findByCode(code).orElseThrow(() -> new IllegalStateException("نقش پیش‌فرض " + code + " پیدا نشد (Flyway V3 اجرا نشده؟)"));
    }

    private AppUser user(String username, String fullName, Team team, AppRole role, String password, boolean mustChange) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setTeam(team);
        u.getRoles().add(role);
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(mustChange);
        return users.save(u);
    }

    private void ev(Report r, AppUser actor, EventType type, Status from, Status to, String note, LocalDateTime at) {
        ReportEvent e = new ReportEvent();
        e.setReport(r);
        e.setActor(actor);
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNoteText(note);
        e.setCreatedAt(at.isAfter(JalaliCalendar.now()) ? JalaliCalendar.now() : at);
        events.save(e);
    }
}
