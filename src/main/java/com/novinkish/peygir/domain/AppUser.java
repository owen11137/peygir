package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** نقش‌های کاربر (هر کاربر می‌تواند چند نقش داشته باشد). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<AppRole> roles = new HashSet<>();

    /** LOCAL یا LDAP (برای اتصال بعدی به Active Directory). */
    @Column(name = "auth_source", nullable = false, length = 10)
    private String authSource = "LOCAL";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** دسترسی‌هایی که علاوه بر نقش‌ها به این کاربر داده شده. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_perm_grant", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Permission> grantedPermissions = new HashSet<>();

    /** دسترسی‌هایی که با وجود نقش‌ها از این کاربر گرفته شده. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_perm_revoke", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Permission> revokedPermissions = new HashSet<>();

    /** دسترسی‌های حاصل از نقش‌ها (بدون استثناهای فردی). */
    public Set<Permission> rolePermissions() {
        Set<Permission> s = EnumSet.noneOf(Permission.class);
        for (AppRole r : roles) s.addAll(r.getPermissions());
        return s;
    }

    /** دسترسی نهایی = نقش‌ها + اعطاشده − سلب‌شده. */
    public Set<Permission> effectivePermissions() {
        Set<Permission> s = rolePermissions();
        s.addAll(grantedPermissions);
        s.removeAll(revokedPermissions);
        return s;
    }

    public boolean has(Permission p) { return effectivePermissions().contains(p); }

    public boolean hasCustomPermissions() { return !grantedPermissions.isEmpty() || !revokedPermissions.isEmpty(); }

    public boolean hasRole(AppRole r) { return roles.stream().anyMatch(x -> x.getId().equals(r.getId())); }

    /** نام نقش‌ها برای نمایش. */
    public String getRoleNames() {
        return roles.stream().map(AppRole::getName).sorted(Comparator.naturalOrder()).collect(Collectors.joining("، "));
    }
}
