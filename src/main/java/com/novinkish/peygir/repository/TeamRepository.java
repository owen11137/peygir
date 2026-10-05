package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByActiveTrueOrderByName();
    List<Team> findAllByOrderByName();
    Optional<Team> findByName(String name);
}
