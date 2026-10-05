package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.ContactMethod;
import com.novinkish.peygir.domain.Report;
import com.novinkish.peygir.service.JalaliCalendar;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReportForm {
    @NotNull(message = "تیم هدف را انتخاب کنید")
    private Long targetTeamId;

    /** شخصی که پاسخ نداده؛ همیشه از لیست کاربران (دسته‌بندی‌شده بر اساس تیم) انتخاب می‌شود. */
    @NotNull(message = "شخصی که پاسخ نداده را انتخاب کنید")
    private Long targetUserId;

    @NotNull(message = "روش تماس را انتخاب کنید")
    private ContactMethod contactMethod = ContactMethod.PHONE;

    @NotBlank(message = "تاریخ و ساعت تماس را وارد کنید (مثل 1405/07/12 14:30)")
    private String contactAt = JalaliCalendar.format(JalaliCalendar.now());

    @NotNull(message = "تعداد دفعات تلاش را وارد کنید")
    @Min(value = 1, message = "تعداد دفعات تلاش حداقل ۱ است")
    @Max(value = 99, message = "تعداد دفعات تلاش حداکثر ۹۹ است")
    private Integer attemptsCount = 1;

    @NotBlank(message = "موضوع تماس را وارد کنید")
    @Size(max = 300, message = "موضوع حداکثر ۳۰۰ نویسه باشد")
    private String subject;

    @NotNull(message = "علت عدم پاسخ را انتخاب کنید")
    private Long reasonId;

    @Size(max = 2000, message = "توضیحات حداکثر ۲۰۰۰ نویسه باشد")
    private String reasonDetail;

    public static ReportForm from(Report r) {
        ReportForm f = new ReportForm();
        f.targetTeamId = r.getTargetTeam().getId();
        f.targetUserId = r.getTargetUser() == null ? null : r.getTargetUser().getId();
        f.contactMethod = r.getContactMethod();
        f.contactAt = JalaliCalendar.format(r.getContactAt());
        f.attemptsCount = r.getAttemptsCount();
        f.subject = r.getSubject();
        f.reasonId = r.getReason().getId();
        f.reasonDetail = r.getReasonDetail();
        return f;
    }
}
