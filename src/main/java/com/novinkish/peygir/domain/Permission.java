package com.novinkish.peygir.domain;

/** دسترسی‌های قابل‌اعطا/سلب برای هر کاربر. نقش‌ها فقط یک مجموعه‌ی پیش‌فرض از این‌ها هستند. */
public enum Permission {
    REPORT_CREATE("ثبت و ویرایش گزارش", "ثبت گزارش جدید، ویرایش پیش‌نویس و ارسال برای تأیید"),
    TEAM_APPROVE("تأیید گزارش‌های تیم", "تأیید / برگشت / رد گزارش‌های تیم خودش؛ گزارش خودش هم مستقیم تأییدشده ثبت می‌شود"),
    TEAM_STATS("آمار تیم خودش", "دیدن داشبورد و آمار گزارش‌های تیم خودش"),
    VIEW_ALL("دیدن همه‌ی گزارش‌های تأییدشده", "داشبورد همه‌ی تیم‌ها و لیست همه‌ی گزارش‌های تأییدشده به بعد"),
    REVIEW_CLOSE("بررسی و بستن گزارش‌ها", "تغییر وضعیت به «در حال بررسی» و «بسته‌شده»"),
    EXPORT("خروجی Excel", "گرفتن خروجی Excel از گزارش‌هایی که می‌بیند"),
    ADMIN_PANEL("مدیریت سیستم", "مدیریت تیم‌ها، کاربران، علت‌ها و دسترسی‌ها"),
    VIEW_EVERYTHING("دیدن همه‌ی گزارش‌ها در هر وضعیت", "برای رفع اشکال؛ شامل پیش‌نویس و در انتظار تأیید");

    private final String label;
    private final String description;
    Permission(String label, String description) { this.label = label; this.description = description; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getAuthority() { return "PERM_" + name(); }
}
