package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, Long> {

    List<ActivityAttendance> findByMember_MemberId(Long memberId);

    List<ActivityAttendance> findByAttendanceDate(LocalDate attendanceDate);

    List<ActivityAttendance> findAllByOrderByAttendanceDateDescRecordedAtDesc(org.springframework.data.domain.Pageable pageable);

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

    @Query("""
            select a.member.memberId as memberId, count(a) as attendanceCount
            from ActivityAttendance a
            where a.status = com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
            and a.member.active = true
            and (:startDate is null or a.attendanceDate >= :startDate)
            and (:endDate is null or a.attendanceDate < :endDate)
            group by a.member.memberId
            """)
    List<MemberAttendanceCountProjection> findAttendanceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select a.member.memberId as memberId, a.activityType.activityTypeId as activityTypeId, count(a) as attendanceCount
            from ActivityAttendance a
            where a.status = com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
            and a.member.active = true
            and a.activityType.active = true
            and (:startDate is null or a.attendanceDate >= :startDate)
            and (:endDate is null or a.attendanceDate < :endDate)
            group by a.member.memberId, a.activityType.activityTypeId
            """)
    List<MemberActivityAttendanceCountProjection> findMemberActivityCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select a.activityType.activityTypeId as activityTypeId, count(distinct a.attendanceDate) as totalCount
            from ActivityAttendance a
            where a.status = com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
            and a.activityType.active = true
            and (:startDate is null or a.attendanceDate >= :startDate)
            and (:endDate is null or a.attendanceDate < :endDate)
            group by a.activityType.activityTypeId
            """)
    List<ActivityOccurrenceCountProjection> findActivityOccurrenceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    interface MemberAttendanceCountProjection {
        Long getMemberId();

        Long getAttendanceCount();
    }

    interface MemberActivityAttendanceCountProjection {
        Long getMemberId();

        Long getActivityTypeId();

        Long getAttendanceCount();
    }

    interface ActivityOccurrenceCountProjection {
        Long getActivityTypeId();

        Long getTotalCount();
    }
}
