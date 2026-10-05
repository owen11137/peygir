package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "report")
@Getter @Setter @NoArgsConstructor
public class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_no", length = 20)
    private String trackingNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "caller_user_id")
    private AppUser caller;

    @ManyToOne(optional = false)
    @JoinColumn(name = "caller_team_id")
    private Team callerTeam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_team_id")
    private Team targetTeam;

    /** شخص هدف (انتخاب از لیست کاربران؛ اختیاری). */
    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private AppUser targetUser;

    /** نسخه‌ی متنی نام شخص هدف (برای نمایش، خروجی Excel و داده‌های قدیمی). */
    @Column(name = "target_person", length = 150)
    private String targetPerson;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false, length = 20)
    private ContactMethod contactMethod;

    @Column(name = "contact_at", nullable = false)
    private LocalDateTime contactAt;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount = 1;

    @Column(nullable = false, length = 300)
    private String subject;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reason_id")
    private Reason reason;

    @Column(name = "reason_detail", length = 2000)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "submitted_by_manager", nullable = false)
    private boolean submittedByManager;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
