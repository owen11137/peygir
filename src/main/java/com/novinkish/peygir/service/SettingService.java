package com.novinkish.peygir.service;

import com.novinkish.peygir.domain.AppSetting;
import com.novinkish.peygir.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** تنظیمات سراسری برنامه (جدول app_setting). */
@Service
@RequiredArgsConstructor
public class SettingService {
    public static final String REFRESH_KEY = "dashboard.refresh.seconds";
    public static final int REFRESH_DEFAULT = 60, REFRESH_MIN = 10, REFRESH_MAX = 3600;

    private final SettingRepository repo;

    @Transactional(readOnly = true)
    public int dashboardRefreshSeconds() {
        return repo.findById(REFRESH_KEY).map(s -> {
            try { return Math.max(REFRESH_MIN, Math.min(REFRESH_MAX, Integer.parseInt(s.getValue().trim()))); }
            catch (NumberFormatException e) { return REFRESH_DEFAULT; }
        }).orElse(REFRESH_DEFAULT);
    }

    @Transactional
    public void setDashboardRefreshSeconds(int seconds) {
        if (seconds < REFRESH_MIN || seconds > REFRESH_MAX)
            throw new BusinessException("زمان به‌روزرسانی باید بین " + REFRESH_MIN + " تا " + REFRESH_MAX + " ثانیه باشد");
        repo.save(new AppSetting(REFRESH_KEY, String.valueOf(seconds)));
    }
}
