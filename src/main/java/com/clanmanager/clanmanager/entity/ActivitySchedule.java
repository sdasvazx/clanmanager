package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "activity_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    // 특정 날짜용 일정
    private LocalDate activityDate;

    // 반복 요일용 일정
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime activityTime;

    @Column(nullable = false)
    private Boolean recurring;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    public void prePersist() {
        this.recurring = this.recurring == null ? true : this.recurring;
        this.active = this.active == null ? true : this.active;
    }
}