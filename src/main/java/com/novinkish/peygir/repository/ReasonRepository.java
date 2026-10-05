package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.Reason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReasonRepository extends JpaRepository<Reason, Long> {
    List<Reason> findByActiveTrueOrderBySortOrder();
    List<Reason> findAllByOrderBySortOrder();
}
