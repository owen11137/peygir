package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_setting")
@Getter @Setter @NoArgsConstructor
public class AppSetting {
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    public AppSetting(String key, String value) { this.key = key; this.value = value; }
}
