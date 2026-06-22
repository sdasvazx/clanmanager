package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);

    List<Attendance> findByMemberIdOrderByAttendanceDateDesc(Long memberId);

    long countByMemberId(Long memberId);
}