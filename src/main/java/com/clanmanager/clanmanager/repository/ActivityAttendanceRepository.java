package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, Long> {

    List<ActivityAttendance> findByMember_MemberId(Long memberId);

    List<ActivityAttendance> findByAttendanceDate(LocalDate attendanceDate);
}