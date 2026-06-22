package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendances")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    private Long memberId;

    private LocalDate attendanceDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getAttendanceId() {
        return attendanceId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }
}