package com.novinkish.peygir.security;

import com.novinkish.peygir.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * احراز هویت محلی (رمز ذخیره‌شده در دیتابیس).
 * برای اتصال به Active Directory بعداً کافی است یک AuthenticationProvider مبتنی بر LDAP
 * در SecurityConfig اضافه شود و این سرویس برای کاربران با authSource=LDAP فقط نقش/تیم را بارگذاری کند.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username.trim())
                .map(PeygirUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
