package com.novinkish.peygir.security;

import com.novinkish.peygir.domain.AppUser;
import com.novinkish.peygir.domain.Permission;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.Set;

/** کاربر واردشده؛ شناسه، تیم، نقش و دسترسی‌های نهایی را برای کنترل دسترسی نگه می‌دارد. */
public class PeygirUser implements UserDetails {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final Long teamId;
    private final String teamName;
    private final String roleNames;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final Set<Permission> permissions;
    private final Set<Long> supervisedTeamIds;
    private final String supervisedTeamNames;

    public PeygirUser(AppUser u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.passwordHash = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.teamId = u.getTeam().getId();
        this.teamName = u.getTeam().getName();
        this.roleNames = u.getRoleNames();
        this.enabled = u.isActive() && u.getTeam().isActive();
        this.mustChangePassword = u.isMustChangePassword();
        this.supervisedTeamIds = u.getSupervisedTeams().stream().map(t -> t.getId()).collect(Collectors.toUnmodifiableSet());
        this.supervisedTeamNames = u.getSupervisedTeams().stream().map(t -> t.getName()).sorted().collect(Collectors.joining("، "));
        Set<Permission> eff = u.effectivePermissions();
        this.permissions = eff.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(eff);
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getRoleNames() { return roleNames; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Set<Permission> getPermissions() { return permissions; }
    public boolean has(Permission p) { return permissions.contains(p); }
    public boolean hasSupervisedTeams() { return !supervisedTeamIds.isEmpty(); }
    public String getSupervisedTeamNames() { return supervisedTeamNames; }
    public Set<Long> getVisibleTeamIds() {
        Set<Long> ids = new HashSet<>(supervisedTeamIds);
        ids.add(teamId);
        return Set.copyOf(ids);
    }
    public boolean canReadTeam(Long id) { return teamId.equals(id) || supervisedTeamIds.contains(id); }
    // An explicit supervision list limits broad viewing permissions to these teams and the home team.
    public boolean seesAll() { return !hasSupervisedTeams() && (has(Permission.VIEW_ALL) || has(Permission.VIEW_EVERYTHING)); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        for (Permission p : permissions) list.add(new SimpleGrantedAuthority(p.getAuthority()));
        if (hasSupervisedTeams()) list.add(new SimpleGrantedAuthority("PERM_TEAM_OVERVIEW"));
        return list;
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
