package com.novinkish.peygir.service;

/** خطای قابل‌نمایش به کاربر (پیام فارسی). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
