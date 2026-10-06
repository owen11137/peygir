package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.Report;
import com.novinkish.peygir.domain.Status;
import com.novinkish.peygir.repository.ReportRepository;
import com.novinkish.peygir.security.PeygirUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * آمار داشبورد برای یک بازه‌ی زمانی دلخواه (بر اساس زمان تماس) و مقایسه با بازه‌ی قبلی هم‌طول.
 * فقط گزارش‌های «تأییدشده به بعد» حساب می‌شوند؛ کاربرانی که فقط «آمار تیم خودش» دارند
 * فقط گزارش‌های ثبت‌شده توسط تیم خودشان را می‌بینند.
 */
@Service
@RequiredArgsConstructor
public class StatsService {
    private final ReportRepository reports;

    private static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};

    // ------------------------------------------------------------ مدل‌های خروجی (JSON و Excel)
    public record Range(String preset, LocalDate from, LocalDate to, String fromJ, String toJ, int days, String bucket,
                        boolean hasPrev, LocalDate prevFrom, LocalDate prevTo, String prevFromJ, String prevToJ) {}
    public record Kpis(long total, long prevTotal, Long deltaPercent, double perDay, long awaiting, long inReview,
                       long closed, long closedPercent, String topTeam, long topCount) {}
    public record TeamRow(String name, long count, long percent, long prev, long delta, Long deltaPercent,
                          double avgAttempts, long open) {}
    public record PersonRow(String name, String team, long count) {}
    public record NameCount(String name, long count) {}
    public record Series(String name, List<Long> data) {}
    public record Trend(String bucket, List<String> labels, List<Series> series, List<Long> total) {}
    public record MatrixRow(String team, List<Long> cells, long total) {}
    public record Matrix(List<String> teams, List<MatrixRow> rows) {}
    public record Latest(Long id, String trackingNo, String callerTeam, String targetTeam, String targetPerson,
                         String reason, String status, String statusClass, String date) {}
    public record Dashboard(Range range, Kpis kpis, List<TeamRow> byTeam, List<PersonRow> byPerson,
                            List<NameCount> byReason, Trend trend, Matrix matrix, List<Latest> latest, String updatedAt) {}

    // ------------------------------------------------------------ تعیین بازه
    @Transactional(readOnly = true)
    public Range resolve(PeygirUser u, String preset, String fromText, String toText) {
        LocalDate today = JalaliCalendar.now().toLocalDate();
        boolean hasText = (fromText != null && !fromText.isBlank()) || (toText != null && !toText.isBlank());
        String p = (preset == null || preset.isBlank()) ? (hasText ? "custom" : "today") : preset;
        LocalDate from, to;
        if (p.equals("custom")) {
            if (fromText == null || fromText.isBlank()) throw new BusinessException("تاریخ شروع بازه را وارد کنید");
            from = JalaliCalendar.parseDate(fromText);
            to = (toText == null || toText.isBlank()) ? today : JalaliCalendar.parseDate(toText);
            if (from == null || to == null)
                throw new BusinessException("قالب تاریخ درست نیست؛ مثل 1405/07/12 وارد کنید");
            if (from.isAfter(to)) throw new BusinessException("تاریخ شروع نمی‌تواند بعد از تاریخ پایان باشد");
        } else {
            to = today;
            int[] j = JalaliCalendar.toJalali(today.getYear(), today.getMonthValue(), today.getDayOfMonth());
            from = switch (p) {
                case "today" -> today;
                case "7" -> today.minusDays(6);
                case "30" -> today.minusDays(29);
                case "90" -> today.minusDays(89);
                case "month" -> jalaliDate(j[0], j[1], 1);
                case "year" -> jalaliDate(j[0], 1, 1);
                case "3y" -> today.minusYears(3);
                case "all" -> earliest(u, today);
                default -> throw new BusinessException("بازه‌ی انتخاب‌شده معتبر نیست");
            };
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        String bucket = days == 1 ? "hour" : days <= 45 ? "day" : days <= 180 ? "week" : days <= 1100 ? "month" : "year";
        boolean hasPrev = !p.equals("all");
        LocalDate prevTo = hasPrev ? from.minusDays(1) : null;
        LocalDate prevFrom = hasPrev ? prevTo.minusDays(days - 1L) : null;
        return new Range(p, from, to, JalaliCalendar.format(from), JalaliCalendar.format(to), days, bucket,
                hasPrev, prevFrom, prevTo,
                hasPrev ? JalaliCalendar.format(prevFrom) : null, hasPrev ? JalaliCalendar.format(prevTo) : null);
    }

    private LocalDate earliest(PeygirUser u, LocalDate fallback) {
        List<Report> first = reports.findAll(scope(u), PageRequest.of(0, 1, Sort.by("contactAt"))).getContent();
        return first.isEmpty() ? fallback : first.get(0).getContactAt().toLocalDate();
    }

    private static LocalDate jalaliDate(int jy, int jm, int jd) {
        int[] g = JalaliCalendar.toGregorian(jy, jm, jd);
        return LocalDate.of(g[0], g[1], g[2]);
    }

    // ------------------------------------------------------------ دسترسی به داده
    private Specification<Report> scope(PeygirUser u) {
        Specification<Report> spec = (root, q, cb) ->
                root.get("status").in(Status.APPROVED, Status.IN_REVIEW, Status.CLOSED);
        if (!u.seesAll()) {
            spec = spec.and((root, q, cb) -> root.get("callerTeam").get("id").in(u.getVisibleTeamIds()));
        }
        return spec;
    }

    private Specification<Report> between(LocalDate from, LocalDate to) {
        return (root, q, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.<LocalDateTime>get("contactAt"), from.atStartOfDay()),
                cb.lessThan(root.<LocalDateTime>get("contactAt"), to.plusDays(1).atStartOfDay()));
    }

    /** گزارش‌های بازه (برای جدول و خروجی Excel). */
    @Transactional(readOnly = true)
    public List<Report> reports(PeygirUser u, Range r) {
        return reports.findAll(scope(u).and(between(r.from(), r.to())), Sort.by(Sort.Direction.DESC, "contactAt", "id"));
    }

    // ------------------------------------------------------------ محاسبه
    @Transactional(readOnly = true)
    public Dashboard dashboard(PeygirUser u, Range range) {
        List<Report> cur = reports(u, range);
        List<Report> prev = range.hasPrev()
                ? reports.findAll(scope(u).and(between(range.prevFrom(), range.prevTo()))) : List.of();
        int total = cur.size();

        // --- عملکرد تیم‌ها (تیمی که پاسخ نداده)
        Map<String, List<Report>> curBy = cur.stream().collect(Collectors.groupingBy(r -> r.getTargetTeam().getName()));
        Map<String, Long> prevBy = prev.stream()
                .collect(Collectors.groupingBy(r -> r.getTargetTeam().getName(), Collectors.counting()));
        Set<String> names = new TreeSet<>(curBy.keySet());
        names.addAll(prevBy.keySet());
        List<TeamRow> byTeam = new ArrayList<>();
        for (String n : names) {
            List<Report> l = curBy.getOrDefault(n, List.of());
            long c = l.size();
            long pv = prevBy.getOrDefault(n, 0L);
            double avg = l.isEmpty() ? 0 : round1(l.stream().mapToInt(Report::getAttemptsCount).average().orElse(0));
            long open = l.stream().filter(r -> r.getStatus() != Status.CLOSED).count();
            Long dp = pv > 0 ? Long.valueOf(Math.round((c - pv) * 100.0 / pv)) : null;
            byTeam.add(new TeamRow(n, c, total == 0 ? 0 : Math.round(c * 100.0 / total), pv, c - pv, dp, avg, open));
        }
        byTeam.sort(Comparator.comparingLong(TeamRow::count).reversed().thenComparing(TeamRow::name));

        // --- افرادی که بیشترین عدم پاسخ را داشته‌اند
        Map<String, Long> persons = cur.stream().filter(r -> r.getTargetPerson() != null)
                .collect(Collectors.groupingBy(r -> r.getTargetPerson() + "\u0000" + r.getTargetTeam().getName(),
                        Collectors.counting()));
        List<PersonRow> byPerson = persons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(e -> {
                    String[] parts = e.getKey().split("\u0000", 2);
                    return new PersonRow(parts[0], parts.length > 1 ? parts[1] : "", e.getValue());
                }).collect(Collectors.toList());

        // --- علت‌ها
        List<NameCount> byReason = cur.stream()
                .collect(Collectors.groupingBy(r -> r.getReason().getTitle(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new NameCount(e.getKey(), e.getValue())).collect(Collectors.toList());

        // --- شاخص‌ها
        long awaiting = cur.stream().filter(r -> r.getStatus() == Status.APPROVED).count();
        long inReview = cur.stream().filter(r -> r.getStatus() == Status.IN_REVIEW).count();
        long closed = cur.stream().filter(r -> r.getStatus() == Status.CLOSED).count();
        TeamRow top = byTeam.stream().filter(t -> t.count() > 0).findFirst().orElse(null);
        Long totalDelta = range.hasPrev() && prev.size() > 0
                ? Long.valueOf(Math.round((total - prev.size()) * 100.0 / prev.size())) : null;
        Kpis kpis = new Kpis(total, prev.size(), totalDelta, round1(total / (double) range.days()), awaiting, inReview,
                closed, total == 0 ? 0 : Math.round(closed * 100.0 / total),
                top == null ? "—" : top.name(), top == null ? 0 : top.count());

        // --- روند زمانی (۵ تیم بدتر + جمع)
        Bucketer b = new Bucketer(range);
        Map<String, Map<String, Long>> perTeam = new HashMap<>();
        Map<String, Long> perTotal = new HashMap<>();
        for (Report r : cur) {
            String k = b.key(r.getContactAt());
            perTeam.computeIfAbsent(r.getTargetTeam().getName(), x -> new HashMap<>()).merge(k, 1L, Long::sum);
            perTotal.merge(k, 1L, Long::sum);
        }
        List<Series> series = new ArrayList<>();
        for (TeamRow t : byTeam) {
            if (t.count() == 0 || series.size() >= 5) continue;
            Map<String, Long> m = perTeam.getOrDefault(t.name(), Map.of());
            series.add(new Series(t.name(), b.keys.stream().map(k -> m.getOrDefault(k, 0L)).collect(Collectors.toList())));
        }
        Trend trend = new Trend(range.bucket(), b.labels,
                series, b.keys.stream().map(k -> perTotal.getOrDefault(k, 0L)).collect(Collectors.toList()));

        // --- ماتریس تیم تماس‌گیرنده × تیم هدف
        TreeSet<String> mNames = new TreeSet<>();
        cur.forEach(r -> { mNames.add(r.getCallerTeam().getName()); mNames.add(r.getTargetTeam().getName()); });
        List<String> teamNames = new ArrayList<>(mNames);
        Map<String, Map<String, Long>> mm = cur.stream().collect(Collectors.groupingBy(
                r -> r.getCallerTeam().getName(),
                Collectors.groupingBy(r -> r.getTargetTeam().getName(), Collectors.counting())));
        List<MatrixRow> rows = new ArrayList<>();
        for (String caller : teamNames) {
            List<Long> cells = new ArrayList<>();
            long sum = 0;
            for (String target : teamNames) {
                long c = mm.getOrDefault(caller, Map.of()).getOrDefault(target, 0L);
                cells.add(c);
                sum += c;
            }
            rows.add(new MatrixRow(caller, cells, sum));
        }

        List<Latest> latest = cur.stream().limit(8).map(r -> new Latest(r.getId(), r.getTrackingNo(),
                r.getCallerTeam().getName(), r.getTargetTeam().getName(), r.getTargetPerson(), r.getReason().getTitle(),
                r.getStatus().getLabel(), r.getStatus().getCssClass(), JalaliCalendar.format(r.getContactAt())))
                .collect(Collectors.toList());

        return new Dashboard(range, kpis, byTeam, byPerson, byReason, trend, new Matrix(teamNames, rows), latest,
                JalaliCalendar.format(JalaliCalendar.now()));
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** بازه‌بندی نمودار روند: ساعتی، روزانه، هفتگی (از شنبه)، ماهانه یا سالانه‌ی شمسی. */
    private static final class Bucketer {
        final String kind;
        final List<String> keys = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        Bucketer(Range r) {
            kind = r.bucket();
            switch (kind) {
                case "hour" -> {
                    for (int h = 0; h < 24; h++) { keys.add(String.valueOf(h)); labels.add(String.format("%02d:00", h)); }
                }
                case "day" -> {
                    for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
                        keys.add(d.toString());
                        labels.add(JalaliCalendar.format(d).substring(5));
                    }
                }
                case "week" -> {
                    LocalDate s = r.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
                    for (; !s.isAfter(r.to()); s = s.plusDays(7)) {
                        keys.add(s.toString());
                        labels.add(JalaliCalendar.format(s).substring(5));
                    }
                }
                case "month" -> {
                    int[] a = j(r.from()), z = j(r.to());
                    int y = a[0], m = a[1];
                    while (y < z[0] || (y == z[0] && m <= z[1])) {
                        keys.add(y + "-" + m);
                        labels.add(MONTHS[m - 1] + " " + y);
                        if (++m > 12) { m = 1; y++; }
                    }
                }
                default -> {
                    int[] a = j(r.from()), z = j(r.to());
                    for (int y = a[0]; y <= z[0]; y++) { keys.add(String.valueOf(y)); labels.add(String.valueOf(y)); }
                }
            }
        }

        String key(LocalDateTime t) {
            return switch (kind) {
                case "hour" -> String.valueOf(t.getHour());
                case "day" -> t.toLocalDate().toString();
                case "week" -> t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).toString();
                case "month" -> { int[] x = j(t.toLocalDate()); yield x[0] + "-" + x[1]; }
                default -> String.valueOf(j(t.toLocalDate())[0]);
            };
        }

        private static int[] j(LocalDate d) { return JalaliCalendar.toJalali(d.getYear(), d.getMonthValue(), d.getDayOfMonth()); }
    }
}
