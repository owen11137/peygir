package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.AppUser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** جستجو و فیلتر کاربران در دیتابیس، پیش از صفحه‌بندی. */
@Getter @Setter
public class UserFilter {
    private String search = "";
    private Long teamId;
    private Boolean active;

    public Specification<AppUser> toSpec() {
        String text = search == null ? "" : search.replace('ي', 'ی').replace('ك', 'ک')
                .replace("\u200c", "").toLowerCase(Locale.ROOT).trim();
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (active != null) conditions.add(cb.equal(root.get("active"), active));
            if (teamId != null) conditions.add(cb.equal(root.get("team").get("id"), teamId));
            if (!text.isEmpty()) {
                Expression<String> name = normalize(cb, root.get("fullName"));
                Expression<String> username = normalize(cb, root.get("username"));
                Expression<String> team = normalize(cb, root.get("team").get("name"));
                for (String word : text.split("\\s+")) {
                    String pattern = "%" + word.replace("\\", "\\\\").replace("%", "\\%")
                            .replace("_", "\\_") + "%";
                    conditions.add(cb.or(cb.like(name, pattern, '\\'), cb.like(username, pattern, '\\'),
                            cb.like(team, pattern, '\\')));
                }
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> normalize(CriteriaBuilder cb, Expression<String> value) {
        Expression<String> normalized = cb.lower(value);
        normalized = cb.function("replace", String.class, normalized, cb.literal("ي"), cb.literal("ی"));
        normalized = cb.function("replace", String.class, normalized, cb.literal("ك"), cb.literal("ک"));
        return cb.function("replace", String.class, normalized, cb.literal("\u200c"), cb.literal(""));
    }
}
