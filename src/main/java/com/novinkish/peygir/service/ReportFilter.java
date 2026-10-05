package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.Report;
import com.novinkish.peygir.domain.Status;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** فیلترهای لیست گزارش‌ها. تاریخ‌ها به صورت شمسی (مثل 1405/07/01) وارد می‌شوند. */
@Getter @Setter
public class ReportFilter {
    private Status status;
    private Long callerTeamId;
    private Long targetTeamId;
    private Long reasonId;
    private String from;
    private String to;

    public Specification<Report> toSpec() {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (callerTeamId != null) p.add(cb.equal(root.get("callerTeam").get("id"), callerTeamId));
            if (targetTeamId != null) p.add(cb.equal(root.get("targetTeam").get("id"), targetTeamId));
            if (reasonId != null) p.add(cb.equal(root.get("reason").get("id"), reasonId));
            LocalDate f = JalaliCalendar.parseDate(from);
            LocalDate t = JalaliCalendar.parseDate(to);
            if (f != null) p.add(cb.greaterThanOrEqualTo(root.<java.time.LocalDateTime>get("contactAt"), f.atStartOfDay()));
            if (t != null) p.add(cb.lessThan(root.<java.time.LocalDateTime>get("contactAt"), t.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
