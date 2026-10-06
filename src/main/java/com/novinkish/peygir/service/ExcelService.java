package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.Report;
import com.novinkish.peygir.domain.ReportEvent;
import com.novinkish.peygir.service.StatsService.Dashboard;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** همه‌ی خروجی‌های Excel برنامه. شیت‌ها راست‌به‌چپ‌اند و ردیف عنوان ثابت است. */
@Service
public class ExcelService {

    private static final String[] REPORT_HEADS = {"شماره پیگیری", "وضعیت", "تیم تماس‌گیرنده", "تماس‌گیرنده", "تیم هدف",
            "شخص هدف", "روش تماس", "زمان تماس", "تعداد تلاش", "موضوع", "علت", "توضیحات", "تاریخ ثبت"};

    // ------------------------------------------------------------ عمومی
    /** جدول ساده‌ی یک‌شیتی. مقادیر عددی به‌صورت عدد ذخیره می‌شوند. */
    public byte[] table(String sheet, String[] heads, List<Object[]> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            fill(wb, sheet, heads, rows);
            return bytes(wb);
        }
    }

    public byte[] exportReports(List<Report> list) throws IOException {
        return table("گزارش‌ها", REPORT_HEADS, reportRows(list));
    }

    /** یک گزارش با تاریخچه‌ی کامل. */
    public byte[] exportReportDetail(Report r, List<ReportEvent> events) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            fill(wb, "گزارش", new String[]{"فیلد", "مقدار"}, List.of(
                    new Object[]{"شماره پیگیری", r.getTrackingNo()},
                    new Object[]{"وضعیت", r.getStatus().getLabel()},
                    new Object[]{"تیم تماس‌گیرنده", r.getCallerTeam().getName()},
                    new Object[]{"تماس‌گیرنده", r.getCaller().getFullName()},
                    new Object[]{"تیم هدف", r.getTargetTeam().getName()},
                    new Object[]{"شخص هدف", nz(r.getTargetPerson())},
                    new Object[]{"روش تماس", r.getContactMethod().getLabel()},
                    new Object[]{"زمان تماس", JalaliCalendar.format(r.getContactAt())},
                    new Object[]{"تعداد دفعات تلاش", r.getAttemptsCount()},
                    new Object[]{"موضوع", r.getSubject()},
                    new Object[]{"علت عدم پاسخ", r.getReason().getTitle()},
                    new Object[]{"توضیحات", nz(r.getReasonDetail())},
                    new Object[]{"تاریخ ثبت", JalaliCalendar.format(r.getCreatedAt())}));
            List<Object[]> ev = new ArrayList<>();
            for (ReportEvent e : events) {
                ev.add(new Object[]{JalaliCalendar.format(e.getCreatedAt()), e.getEventType().getLabel(),
                        e.getActor().getFullName(), e.getActor().getTeam().getName(),
                        e.getFromStatus() == null ? "" : e.getFromStatus().getLabel(),
                        e.getToStatus() == null ? "" : e.getToStatus().getLabel(),
                        nz(e.getNoteText()), nz(e.getChangesText())});
            }
            fill(wb, "تاریخچه", new String[]{"زمان", "رویداد", "انجام‌دهنده", "تیم", "از وضعیت", "به وضعیت", "توضیح", "تغییرات"}, ev);
            return bytes(wb);
        }
    }

    /** داشبورد برای بازه‌ی انتخاب‌شده: خلاصه، عملکرد تیم‌ها، افراد، علت‌ها، روند، ماتریس و فهرست گزارش‌ها. */
    public byte[] exportDashboard(Dashboard d, List<Report> list) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            var k = d.kpis();
            var r = d.range();
            List<Object[]> sum = new ArrayList<>();
            sum.add(new Object[]{"بازه", r.fromJ() + " تا " + r.toJ() + " (" + r.days() + " روز)"});
            if (r.hasPrev()) sum.add(new Object[]{"بازه‌ی مقایسه (قبلی)", r.prevFromJ() + " تا " + r.prevToJ()});
            sum.add(new Object[]{"کل گزارش‌ها", k.total()});
            if (r.hasPrev()) {
                sum.add(new Object[]{"گزارش‌های بازه‌ی قبل", k.prevTotal()});
                sum.add(new Object[]{"تغییر نسبت به بازه‌ی قبل (٪)", k.deltaPercent() == null ? "—" : k.deltaPercent()});
            }
            sum.add(new Object[]{"میانگین روزانه", k.perDay()});
            sum.add(new Object[]{"منتظر بررسی مدیریت", k.awaiting()});
            sum.add(new Object[]{"در حال بررسی", k.inReview()});
            sum.add(new Object[]{"بسته‌شده", k.closed()});
            sum.add(new Object[]{"درصد بسته‌شده", k.closedPercent()});
            sum.add(new Object[]{"بیشترین عدم پاسخ", k.topTeam() + " (" + k.topCount() + ")"});
            fill(wb, "خلاصه", new String[]{"شاخص", "مقدار"}, sum);

            List<Object[]> teams = new ArrayList<>();
            d.byTeam().forEach(t -> teams.add(new Object[]{t.name(), t.count(), t.percent(), t.prev(), t.delta(),
                    t.deltaPercent() == null ? "—" : t.deltaPercent(), t.avgAttempts(), t.open()}));
            fill(wb, "عملکرد تیم‌ها", new String[]{"تیم", "تعداد عدم پاسخ", "سهم از کل (٪)", "بازه‌ی قبل", "تغییر",
                    "تغییر (٪)", "میانگین تلاش", "باز (بسته‌نشده)"}, teams);

            List<Object[]> persons = new ArrayList<>();
            d.byPerson().forEach(p -> persons.add(new Object[]{p.name(), p.team(), p.count()}));
            fill(wb, "افراد", new String[]{"شخص", "تیم", "تعداد عدم پاسخ"}, persons);

            List<Object[]> reasons = new ArrayList<>();
            d.byReason().forEach(x -> reasons.add(new Object[]{x.name(), x.count()}));
            fill(wb, "علت‌ها", new String[]{"علت", "تعداد"}, reasons);

            var tr = d.trend();
            List<String> th = new ArrayList<>(List.of("بازه"));
            tr.series().forEach(s -> th.add(s.name()));
            th.add("جمع");
            List<Object[]> trows = new ArrayList<>();
            for (int i = 0; i < tr.labels().size(); i++) {
                Object[] row = new Object[th.size()];
                row[0] = tr.labels().get(i);
                for (int s = 0; s < tr.series().size(); s++) row[s + 1] = tr.series().get(s).data().get(i);
                row[th.size() - 1] = tr.total().get(i);
                trows.add(row);
            }
            fill(wb, "روند", th.toArray(new String[0]), trows);

            var m = d.matrix();
            List<String> mh = new ArrayList<>(List.of("تماس‌گیرنده \\ تیم هدف"));
            mh.addAll(m.teams());
            mh.add("جمع");
            List<Object[]> mrows = new ArrayList<>();
            m.rows().forEach(row -> {
                Object[] o = new Object[mh.size()];
                o[0] = row.team();
                for (int i = 0; i < row.cells().size(); i++) o[i + 1] = row.cells().get(i);
                o[mh.size() - 1] = row.total();
                mrows.add(o);
            });
            fill(wb, "ماتریس تیم‌ها", mh.toArray(new String[0]), mrows);

            fill(wb, "گزارش‌ها", REPORT_HEADS, reportRows(list));
            return bytes(wb);
        }
    }

    public byte[] usersTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            fill(wb, "کاربران", new String[]{"تیم", "نام و نام خانوادگی", "نام کاربری", "نقش (چند نقش با ویرگول)", "رمز اولیه (اختیاری)"},
                    List.of(new Object[]{"امنیت", "علی رضایی", "a.rezaei", "مدیر تیم", ""},
                            new Object[]{"امنیت", "سارا احمدی", "s.ahmadi", "عضو تیم", ""},
                            new Object[]{"مدیریت", "نام مدیرعامل", "ceo", "مدیرعامل / مدیریت ارشد", ""}));
            fill(wb, "راهنما", new String[]{"توضیح"}, List.of(
                    new Object[]{"ستون «نقش» باید نام دقیق نقش‌هایی باشد که در صفحه‌ی «نقش‌ها» تعریف شده؛ برای چند نقش آن‌ها را با ویرگول جدا کنید."},
                    new Object[]{"اگر تیم وجود نداشته باشد ساخته می‌شود. در ورود معمولی، رمز خالی برای کاربر جدید یعنی رمز پیش‌فرض؛ تغییر اجباری رمز فقط با تیک فرم فعال می‌شود. در جایگزینی، رمز خالی کاربر موجود رمز قبلی او را حفظ می‌کند."}));
            return bytes(wb);
        }
    }

    // ------------------------------------------------------------ کمکی
    private List<Object[]> reportRows(List<Report> list) {
        List<Object[]> rows = new ArrayList<>();
        for (Report r : list) {
            rows.add(new Object[]{r.getTrackingNo(), r.getStatus().getLabel(), r.getCallerTeam().getName(),
                    r.getCaller().getFullName(), r.getTargetTeam().getName(), nz(r.getTargetPerson()),
                    r.getContactMethod().getLabel(), JalaliCalendar.format(r.getContactAt()), r.getAttemptsCount(),
                    r.getSubject(), r.getReason().getTitle(), nz(r.getReasonDetail()),
                    JalaliCalendar.format(r.getCreatedAt())});
        }
        return rows;
    }

    private void fill(Workbook wb, String name, String[] heads, List<Object[]> rows) {
        Sheet sh = wb.createSheet(name);
        sh.setRightToLeft(true);
        CellStyle head = headStyle(wb);
        Row hr = sh.createRow(0);
        for (int i = 0; i < heads.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(head);
        }
        int rn = 1;
        for (Object[] row : rows) {
            Row r = sh.createRow(rn++);
            for (int i = 0; i < row.length; i++) {
                Object v = row[i];
                Cell c = r.createCell(i);
                if (v instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue(v == null ? "" : String.valueOf(v));
            }
        }
        for (int i = 0; i < heads.length; i++) {
            sh.autoSizeColumn(i);
            int w = sh.getColumnWidth(i);
            sh.setColumnWidth(i, Math.max(3500, Math.min(w + 900, 14000)));
        }
        sh.createFreezePane(0, 1);
    }

    private byte[] bytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private CellStyle headStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
