package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<AppSetting, String> {
}
