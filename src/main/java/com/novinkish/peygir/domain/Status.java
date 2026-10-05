package com.novinkish.peygir.domain;

/** وضعیت‌های گزارش. cssClass نام کلاس رنگ برچسب در رابط کاربری است. */
public enum Status {
    DRAFT("پیش‌نویس", "secondary"),
    PENDING("در انتظار تأیید مدیر تیم", "warning"),
    NEEDS_FIX("نیاز به اصلاح", "orange"),
    APPROVED("تأییدشده", "primary"),
    REJECTED("رد شده", "danger"),
    IN_REVIEW("در حال بررسی", "info"),
    CLOSED("بسته‌شده", "success");

    private final String label;
    private final String cssClass;
    Status(String label, String cssClass) { this.label = label; this.cssClass = cssClass; }
    public String getLabel() { return label; }
    public String getCssClass() { return cssClass; }

    /** گزارش‌هایی که به مدیریت ارشد می‌رسند (تأییدشده به بعد). */
    public boolean isValidated() { return this == APPROVED || this == IN_REVIEW || this == CLOSED; }
}
