package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, Long> {

    List<ActivityAttendance> findByMember_MemberId(Long memberId);

    List<ActivityAttendance> findByAttendanceDate(LocalDate attendanceDate);

    List<ActivityAttendance> findByAttendanceDateBetweenOrderByAttendanceDateDescRecordedAtDesc(LocalDate startDate, LocalDate endDate);

    boolean existsByMemberAndActivityTypeAndAttendanceDate(Member member, com.clanmanager.clanmanager.entity.ActivityType activityType, LocalDate attendanceDate);

    void deleteByMemberAndActivityTypeAndAttendanceDate(Member member, ActivityType activityType, LocalDate attendanceDate);

    long countByMemberAndStatus(Member member, AttendanceStatus status);

    @Query("""
            select count(a)
            from ActivityAttendance a
            where a.status = com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
            and a.member.active = true
            group by a.member
            order by count(a) desc
            """)
    List<Long> findAttendanceCountsByMember(Pageable pageable);
}
