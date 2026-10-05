package com.novinkish.peygir.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** نقش = الگوی پیش‌فرض دسترسی. ادمین می‌تواند برای هر کاربر دسترسی‌ها را کم یا زیاد کند. */
public enum Role {
    USER("عضو تیم", Permission.REPORT_CREATE),
    TEAM_MANAGER("مدیر تیم", Permission.REPORT_CREATE, Permission.TEAM_APPROVE, Permission.TEAM_STATS, Permission.EXPORT),
    SENIOR_MANAGER("مدیرعامل / مدیریت ارشد", Permission.VIEW_ALL, Permission.REVIEW_CLOSE, Permission.EXPORT),
    ADMIN("ادمین", Permission.ADMIN_PANEL, Permission.VIEW_ALL, Permission.VIEW_EVERYTHING, Permission.EXPORT);

    private final String label;
    private final Set<Permission> defaults;

    Role(String label, Permission... defaults) {
        this.label = label;
        this.defaults = defaults.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(defaults));
    }

    public String getLabel() { return label; }

    public Set<Permission> defaultPermissions() { return EnumSet.copyOf(defaults.isEmpty() ? EnumSet.noneOf(Permission.class) : defaults); }

    /** برای استفاده در JavaScript صفحه‌ی ادمین. */
    public String getDefaultCsv() { return defaults.stream().map(Enum::name).collect(Collectors.joining(",")); }
}
