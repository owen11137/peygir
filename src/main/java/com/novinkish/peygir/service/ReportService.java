package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import com.novinkish.peygir.security.PeygirUser;
import com.novinkish.peygir.web.ReportForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * منطق اصلی گردش کار گزارش: ثبت، ارسال، تأیید/برگشت/رد، بررسی و بستن + تاریخچه.
 * همه‌ی کنترل‌های دسترسی دقیق (تیم، مالکیت، وضعیت) اینجاست، نه در کنترلرها.
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reports;
    private final ReportEventRepository events;
    private final UserRepository users;
    private final TeamRepository teams;
    private final ReasonRepository reasons;

    // ------------------------------------------------------------------ دسترسی

    /** گزارش‌هایی که این کاربر مجاز به دیدنشان است. */
    public Specification<Report> visible(PeygirUser u) {
        if (!u.hasSupervisedTeams() && u.has(Permission.VIEW_EVERYTHING)) return (root, q, cb) -> cb.conjunction();
        // تیم اصلی و تیم‌های تحت نظارت؛ پیش‌نویس دیگران پنهان است.
        Specification<Report> team = (root, q, cb) -> cb.and(
                root.get("callerTeam").get("id").in(u.getVisibleTeamIds()),
                cb.or(cb.notEqual(root.get("status"), Status.DRAFT),
                      cb.equal(root.get("caller").get("id"), u.getId())));
        if (u.seesAll()) {
            Specification<Report> validated = (root, q, cb) ->
                    root.get("status").in(Status.APPROVED, Status.IN_REVIEW, Status.CLOSED);
            return team.or(validated);
        }
        return team;
    }

    public boolean canView(PeygirUser u, Report r) {
        if (!u.hasSupervisedTeams() && u.has(Permission.VIEW_EVERYTHING)) return true;
        if (u.seesAll() && r.getStatus().isValidated()) return true;
        return u.canReadTeam(r.getCallerTeam().getId())
                && (r.getStatus() != Status.DRAFT || r.getCaller().getId().equals(u.getId()));
    }

    public boolean canEdit(PeygirUser u, Report r) {
        return u.has(Permission.REPORT_CREATE) && r.getCaller().getId().equals(u.getId())
                && (!u.hasSupervisedTeams() || r.getCallerTeam().getId().equals(u.getTeamId()))
                && (r.getStatus() == Status.DRAFT || r.getStatus() == Status.NEEDS_FIX);
    }

    public boolean canDecide(PeygirUser u, Report r) {
        return u.has(Permission.TEAM_APPROVE) && r.getStatus() == Status.PENDING
                && r.getCallerTeam().getId().equals(u.getTeamId())
                && !r.getCaller().getId().equals(u.getId());
    }

    public boolean canReview(PeygirUser u, Report r) {
        return u.has(Permission.REVIEW_CLOSE) && r.getStatus() == Status.APPROVED
                && (!u.hasSupervisedTeams() || r.getCallerTeam().getId().equals(u.getTeamId()));
    }

    public boolean canClose(PeygirUser u, Report r) {
        return u.has(Permission.REVIEW_CLOSE)
                && (!u.hasSupervisedTeams() || r.getCallerTeam().getId().equals(u.getTeamId()))
                && (r.getStatus() == Status.APPROVED || r.getStatus() == Status.IN_REVIEW);
    }

    // ------------------------------------------------------------------ خواندن

    @Transactional(readOnly = true)
    public Report getVisible(PeygirUser u, Long id) {
        Report r = reports.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canView(u, r)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return r;
    }

    @Transactional(readOnly = true)
    public Report getEditable(PeygirUser u, Long id) {
        Report r = getVisible(u, id);
        if (!canEdit(u, r)) throw new AccessDeniedException("edit");
        return r;
    }

    @Transactional(readOnly = true)
    public List<ReportEvent> timeline(Report r) {
        return events.findByReportOrderByCreatedAtAscIdAsc(r);
    }

    @Transactional(readOnly = true)
    public List<Report> list(PeygirUser u, ReportFilter f, boolean mineOnly) {
        Specification<Report> spec = visible(u);
        if (f != null) spec = spec.and(f.toSpec());
        if (mineOnly) spec = spec.and((root, q, cb) -> cb.equal(root.get("caller").get("id"), u.getId()));
        return reports.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    /** صف تأیید مدیر تیم. */
    @Transactional(readOnly = true)
    public List<Report> pendingForManager(PeygirUser u) {
        Specification<Report> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("callerTeam").get("id"), u.getTeamId()),
                cb.equal(root.get("status"), Status.PENDING));
        return reports.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
    }

    @Transactional(readOnly = true)
    public long pendingCountForManager(PeygirUser u) {
        return pendingForManager(u).size();
    }

    // ------------------------------------------------------------------ اعتبارسنجی فرم

    @Transactional(readOnly = true)
    public void validateForm(PeygirUser u, ReportForm f, BindingResult br) {
        if (f.getTargetTeamId() != null) {
            Team t = teams.findById(f.getTargetTeamId()).orElse(null);
            if (t == null || !t.isActive()) br.rejectValue("targetTeamId", "invalid", "تیم هدف معتبر نیست");
            else if (t.getId().equals(u.getTeamId()))
                br.rejectValue("targetTeamId", "same", "تیم هدف نمی‌تواند تیم خودتان باشد");
        }
        if (f.getTargetUserId() != null) {
            AppUser tu = users.findById(f.getTargetUserId()).orElse(null);
            if (tu == null || !tu.isActive()) br.rejectValue("targetUserId", "invalid", "شخص انتخاب‌شده معتبر نیست");
            else if (f.getTargetTeamId() != null && !tu.getTeam().getId().equals(f.getTargetTeamId()))
                br.rejectValue("targetUserId", "mismatch", "شخص انتخاب‌شده عضو تیم هدف نیست");
        }
        if (f.getContactAt() != null && !f.getContactAt().isBlank()) {
            LocalDateTime at = JalaliCalendar.parseDateTime(f.getContactAt());
            if (at == null) br.rejectValue("contactAt", "invalid", "قالب تاریخ درست نیست (مثل 1405/07/12 14:30)");
            else if (at.isAfter(JalaliCalendar.now().plusMinutes(5)))
                br.rejectValue("contactAt", "future", "زمان تماس نمی‌تواند در آینده باشد");
        }
        if (f.getReasonId() != null) {
            Reason r = reasons.findById(f.getReasonId()).orElse(null);
            if (r == null || !r.isActive()) br.rejectValue("reasonId", "invalid", "علت انتخاب‌شده معتبر نیست");
            else if (r.isRequiresDetail() && (f.getReasonDetail() == null || f.getReasonDetail().isBlank()))
                br.rejectValue("reasonDetail", "required", "برای این علت، توضیحات اجباری است");
        }
    }

    // ------------------------------------------------------------------ ثبت و ویرایش

    @Transactional
    public Report create(PeygirUser u, ReportForm f, boolean submit) {
        AppUser caller = users.findById(u.getId()).orElseThrow();
        Report r = new Report();
        r.setCaller(caller);
        r.setCallerTeam(caller.getTeam());
        apply(r, f);
        LocalDateTime now = JalaliCalendar.now();
        r.setStatus(Status.DRAFT);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        r = reports.save(r);
        r.setTrackingNo(String.format("PG-%05d", r.getId()));
        reports.save(r);
        log(r, caller, EventType.CREATED, null, Status.DRAFT, null, null);
        if (submit) doSubmit(r, caller);
        return r;
    }

    @Transactional
    public Report update(PeygirUser u, Long id, ReportForm f, boolean submit) {
        Report r = getEditable(u, id);
        AppUser actor = users.findById(u.getId()).orElseThrow();
        Map<String, String> before = snapshot(r);
        apply(r, f);
        String diff = diff(before, snapshot(r));
        r.setUpdatedAt(JalaliCalendar.now());
        reports.save(r);
        if (!diff.isEmpty()) log(r, actor, EventType.EDITED, r.getStatus(), r.getStatus(), null, diff);
        if (submit) doSubmit(r, actor);
        return r;
    }

    @Transactional
    public void submit(PeygirUser u, Long id) {
        Report r = getEditable(u, id);
        doSubmit(r, users.findById(u.getId()).orElseThrow());
    }

    /** ارسال: عضو عادی ← در انتظار تأیید؛ مدیر تیم ← مستقیم تأییدشده. */
    private void doSubmit(Report r, AppUser actor) {
        if (actor.has(Permission.TEAM_APPROVE)) {
            r.setSubmittedByManager(true);
            move(r, actor, Status.APPROVED, EventType.SUBMITTED_BY_MANAGER, null);
        } else {
            move(r, actor, Status.PENDING, EventType.SUBMITTED, null);
        }
    }

    // ------------------------------------------------------------------ تصمیم مدیر تیم

    @Transactional
    public void approve(PeygirUser u, Long id, String note) {
        Report r = decidable(u, id);
        move(r, actor(u), Status.APPROVED, EventType.APPROVED, clean(note));
    }

    @Transactional
    public void returnForFix(PeygirUser u, Long id, String note) {
        Report r = decidable(u, id);
        move(r, actor(u), Status.NEEDS_FIX, EventType.RETURNED, required(note, "برای برگشت گزارش، نوشتن توضیح الزامی است"));
    }

    @Transactional
    public void reject(PeygirUser u, Long id, String note) {
        Report r = decidable(u, id);
        move(r, actor(u), Status.REJECTED, EventType.REJECTED, required(note, "برای رد گزارش، نوشتن دلیل الزامی است"));
    }

    // ------------------------------------------------------------------ مدیریت ارشد

    @Transactional
    public void startReview(PeygirUser u, Long id, String note) {
        Report r = getVisible(u, id);
        if (!canReview(u, r)) throw new AccessDeniedException("review");
        move(r, actor(u), Status.IN_REVIEW, EventType.IN_REVIEW, clean(note));
    }

    @Transactional
    public void close(PeygirUser u, Long id, String note) {
        Report r = getVisible(u, id);
        if (!canClose(u, r)) throw new AccessDeniedException("close");
        move(r, actor(u), Status.CLOSED, EventType.CLOSED, clean(note));
    }

    // ------------------------------------------------------------------ کمکی

    private Report decidable(PeygirUser u, Long id) {
        Report r = getVisible(u, id);
        if (!canDecide(u, r)) throw new AccessDeniedException("decide");
        return r;
    }

    private AppUser actor(PeygirUser u) { return users.findById(u.getId()).orElseThrow(); }

    private void move(Report r, AppUser actor, Status to, EventType type, String note) {
        Status from = r.getStatus();
        r.setStatus(to);
        r.setUpdatedAt(JalaliCalendar.now());
        reports.save(r);
        log(r, actor, type, from, to, note, null);
    }

    private void log(Report r, AppUser actor, EventType type, Status from, Status to, String note, String changes) {
        ReportEvent e = new ReportEvent();
        e.setReport(r);
        e.setActor(actor);
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNoteText(note);
        e.setChangesText(changes);
        e.setCreatedAt(JalaliCalendar.now());
        events.save(e);
    }

    private void apply(Report r, ReportForm f) {
        r.setTargetTeam(teams.findById(f.getTargetTeamId()).orElseThrow());
        AppUser tu = users.findById(f.getTargetUserId()).orElseThrow();
        r.setTargetUser(tu);
        r.setTargetPerson(tu.getFullName());
        r.setContactMethod(f.getContactMethod());
        r.setContactAt(Objects.requireNonNull(JalaliCalendar.parseDateTime(f.getContactAt())));
        r.setAttemptsCount(f.getAttemptsCount());
        r.setSubject(f.getSubject().trim());
        r.setReason(reasons.findById(f.getReasonId()).orElseThrow());
        r.setReasonDetail(clean(f.getReasonDetail()));
    }

    private Map<String, String> snapshot(Report r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("تیم هدف", r.getTargetTeam().getName());
        m.put("شخص هدف", nz(r.getTargetPerson()));
        m.put("روش تماس", r.getContactMethod().getLabel());
        m.put("زمان تماس", JalaliCalendar.format(r.getContactAt()));
        m.put("تعداد دفعات تلاش", String.valueOf(r.getAttemptsCount()));
        m.put("موضوع", r.getSubject());
        m.put("علت", r.getReason().getTitle());
        m.put("توضیحات", nz(r.getReasonDetail()));
        return m;
    }

    private String diff(Map<String, String> a, Map<String, String> b) {
        StringBuilder sb = new StringBuilder();
        for (var en : a.entrySet()) {
            String nv = b.get(en.getKey());
            if (!Objects.equals(en.getValue(), nv))
                sb.append(en.getKey()).append(": «").append(en.getValue()).append("» ← «").append(nv).append("»\n");
        }
        return sb.toString().trim();
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String clean(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
    private static String required(String s, String msg) {
        if (s == null || s.isBlank()) throw new BusinessException(msg);
        return s.trim();
    }
}
