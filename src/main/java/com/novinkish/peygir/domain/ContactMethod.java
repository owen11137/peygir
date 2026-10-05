package com.novinkish.peygir.domain;

public enum ContactMethod {
    PHONE("تلفن"), MESSENGER("پیام‌رسان"), EMAIL("ایمیل"), TICKET("تیکت");

    private final String label;
    ContactMethod(String label) { this.label = label; }
    public String getLabel() { return label; }
}
