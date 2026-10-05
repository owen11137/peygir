package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<AppUser> findAllByOrderByFullName();
    long countByRolesId(Long roleId);
}
