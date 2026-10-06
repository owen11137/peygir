package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    boolean existsByCallerTeamIdOrTargetTeamId(Long callerTeamId, Long targetTeamId);
}
