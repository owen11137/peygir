package com.novinkish.peygir.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reason")
@Getter @Setter @NoArgsConstructor
public class Reason {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** اگر true باشد، توضیح تکمیلی هنگام انتخاب این علت اجباری است (مثلاً «سایر»). */
    @Column(name = "requires_detail", nullable = false)
    private boolean requiresDetail;

    @Column(nullable = false)
    private boolean active = true;
}
