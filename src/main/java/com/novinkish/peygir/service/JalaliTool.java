package com.novinkish.peygir.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** در قالب‌ها: ${@jalali.dt(report.contactAt)} */
@Component("jalali")
public class JalaliTool {
    public String dt(LocalDateTime t) { return JalaliCalendar.format(t); }
    public String d(LocalDate d) { return JalaliCalendar.format(d); }
    public String now() { return JalaliCalendar.format(JalaliCalendar.now()); }
}
