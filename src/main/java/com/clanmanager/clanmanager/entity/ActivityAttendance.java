package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "activity_attendances",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_activity_date_record",
                        columnNames = {"member_id", "activity_type_id", "attendance_date", "boss_participation_record_id"}
                )
        },
        indexes = {
                @Index(name = "idx_attendance_date_status", columnList = "attendance_date, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boss_participation_record_id")
    private BossParticipationRecord bossParticipationRecord;

    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        this.status = this.status == null ? AttendanceStatus.ATTENDED : this.status;
        this.recordedAt = LocalDateTime.now();
    }
}
