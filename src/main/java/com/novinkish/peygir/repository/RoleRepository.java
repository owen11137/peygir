package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<AppRole, Long> {
    List<AppRole> findAllByOrderByName();
    Optional<AppRole> findByCode(String code);
    Optional<AppRole> findByName(String name);
}
