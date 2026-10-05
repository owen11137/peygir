package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.Permission;
import com.novinkish.peygir.security.PeygirUser;
import com.novinkish.peygir.service.BusinessException;
import com.novinkish.peygir.service.ExcelService;
import com.novinkish.peygir.service.SettingService;
import com.novinkish.peygir.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final StatsService stats;
    private final SettingService settings;
    private final ExcelService excel;

    @GetMapping("/dashboard")
    public String page(Model model) {
        model.addAttribute("refreshSeconds", settings.dashboardRefreshSeconds());
        return "dashboard";
    }

    /** داده‌ی داشبورد برای بازه: preset = today | 7 | 30 | 90 | month | year | 3y | all | custom (+ from/to شمسی). */
    @GetMapping("/dashboard/data")
    @ResponseBody
    public ResponseEntity<?> data(@AuthenticationPrincipal PeygirUser u,
                                  @RequestParam(required = false) String preset,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to) {
        try {
            return ResponseEntity.ok(stats.dashboard(u, stats.resolve(u, preset, from, to)));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/dashboard/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PeygirUser u,
                                         @RequestParam(required = false) String preset,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) throws Exception {
        if (!u.seesAll() && !u.has(Permission.TEAM_STATS)) throw new AccessDeniedException("dashboard");
        StatsService.Range range = stats.resolve(u, preset, from, to);
        byte[] data = excel.exportDashboard(stats.dashboard(u, range), stats.reports(u, range));
        String name = "peygir-dashboard-" + range.fromJ().replace("/", "-") + "_" + range.toJ().replace("/", "-") + ".xlsx";
        return Xlsx.ok(name, data);
    }
}
