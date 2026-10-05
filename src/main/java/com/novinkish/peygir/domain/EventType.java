package com.novinkish.peygir.domain;

public enum EventType {
    CREATED("ثبت پیش‌نویس"),
    EDITED("ویرایش"),
    SUBMITTED("ارسال برای تأیید مدیر تیم"),
    SUBMITTED_BY_MANAGER("ثبت و تأیید مستقیم توسط مدیر تیم"),
    APPROVED("تأیید مدیر تیم"),
    RETURNED("برگشت برای اصلاح"),
    REJECTED("رد شد"),
    IN_REVIEW("شروع بررسی توسط مدیریت"),
    CLOSED("بسته شد");

    private final String label;
    EventType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
