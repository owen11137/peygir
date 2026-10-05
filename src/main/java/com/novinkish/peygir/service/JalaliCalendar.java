package com.novinkish.peygir.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** تبدیل تاریخ میلادی و شمسی (الگوریتم حسابی) و قالب‌بندی/تجزیه‌ی رشته‌ی تاریخ شمسی. */
public final class JalaliCalendar {
    public static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private static final int[] G_DAYS = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

    private JalaliCalendar() {}

    public static LocalDateTime now() { return LocalDateTime.now(TEHRAN).withSecond(0).withNano(0); }

    public static int[] toJalali(int gy, int gm, int gd) {
        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + G_DAYS[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new int[]{jy, jm, jd};
    }

    public static int[] toGregorian(int jy, int jm, int jd) {
        jy += 1595;
        int days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd
                + ((jm < 7) ? (jm - 1) * 31 : ((jm - 7) * 30) + 186);
        int gy = 400 * (days / 146097);
        days %= 146097;
        if (days > 36524) {
            days--;
            gy += 100 * (days / 36524);
            days %= 36524;
            if (days >= 365) days++;
        }
        gy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            gy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int gd = days + 1;
        boolean leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0);
        int[] sal = {0, 31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm];
            gm++;
        }
        return new int[]{gy, gm, gd};
    }

    public static String format(LocalDate d) {
        if (d == null) return "";
        int[] j = toJalali(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        return String.format("%04d/%02d/%02d", j[0], j[1], j[2]);
    }

    public static String format(LocalDateTime t) {
        if (t == null) return "";
        return format(t.toLocalDate()) + " " + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /** تبدیل ارقام فارسی/عربی به لاتین. */
    public static String normalizeDigits(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** ورودی مثل 1405/07/12 (جداکننده / یا -). در صورت نامعتبر بودن null برمی‌گرداند. */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        String[] p = normalizeDigits(text.trim()).split("[/\\-.]");
        if (p.length != 3) return null;
        try {
            int jy = Integer.parseInt(p[0].trim()), jm = Integer.parseInt(p[1].trim()), jd = Integer.parseInt(p[2].trim());
            if (jy < 1300 || jy > 1600 || jm < 1 || jm > 12 || jd < 1 || jd > 31) return null;
            if (jm > 6 && jd > 30) return null;
            int[] g = toGregorian(jy, jm, jd);
            return LocalDate.of(g[0], g[1], g[2]);
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    /** ورودی مثل "1405/07/12 14:30"؛ اگر ساعت نیامده باشد 00:00 در نظر گرفته می‌شود. */
    public static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = normalizeDigits(text.trim()).split("\\s+");
        LocalDate d = parseDate(parts[0]);
        if (d == null) return null;
        int h = 0, m = 0;
        if (parts.length > 1) {
            String[] hm = parts[1].split(":");
            try {
                h = Integer.parseInt(hm[0]);
                m = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
            } catch (NumberFormatException e) {
                return null;
            }
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
        }
        return d.atTime(h, m);
    }
}
