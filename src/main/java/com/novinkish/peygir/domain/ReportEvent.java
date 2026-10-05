package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** تاریخچه‌ی فقط‌افزودنی هر گزارش. */
@Entity
@Table(name = "report_event")
@Getter @Setter @NoArgsConstructor
public class ReportEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actor_user_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private Status toStatus;

    @Column(name = "note_text", length = 2000)
    private String noteText;

    @Column(name = "changes_text", length = 4000)
    private String changesText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
