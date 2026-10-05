package com.novinkish.peygir.repository;

import com.novinkish.peygir.domain.Report;
import com.novinkish.peygir.domain.ReportEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportEventRepository extends JpaRepository<ReportEvent, Long> {
    List<ReportEvent> findByReportOrderByCreatedAtAscIdAsc(Report report);
}
