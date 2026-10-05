package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** نقش = مجموعه‌ای نام‌دار از دسترسی‌ها؛ ادمین می‌تواند بسازد، ویرایش کند، بدهد و بگیرد. */
@Entity
@Table(name = "app_role")
@Getter @Setter @NoArgsConstructor
public class AppRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** فقط برای نقش‌های پیش‌فرض سیستم (USER, TEAM_MANAGER, SENIOR_MANAGER, ADMIN). */
    @Column(length = 40, unique = true)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    /** برای استفاده در JavaScript صفحه‌ی ادمین. */
    public String getPermissionCsv() {
        return permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
