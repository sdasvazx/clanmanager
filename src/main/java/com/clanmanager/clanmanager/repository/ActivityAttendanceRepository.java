package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivityAttendanceRepository
        extends JpaRepository<ActivityAttendance, Long> {

    /*
     * 특정 회원의 전체 출석 기록
     */
    List<ActivityAttendance> findByMember_MemberId(Long memberId);

    /*
     * 특정 날짜의 출석 기록
     */
    List<ActivityAttendance> findByAttendanceDate(LocalDate attendanceDate);

    /*
     * 최근 출석 기록 조회
     */
    List<ActivityAttendance>
    findAllByOrderByAttendanceDateDescRecordedAtDesc(Pageable pageable);

    /*
     * 회차 기간 출석 기록 조회
     *
     * Spring Data JPA의 Between은
     * 시작일과 종료일을 모두 포함한다.
     */
    List<ActivityAttendance>
    findByAttendanceDateBetweenOrderByAttendanceDateDescRecordedAtDesc(
            LocalDate startDate,
            LocalDate endDate
    );

    /*
     * 동일 회원, 활동 종류, 날짜의 출석 기록 존재 여부
     */
    boolean existsByMemberAndActivityTypeAndAttendanceDate(
            Member member,
            ActivityType activityType,
            LocalDate attendanceDate
    );

    Optional<ActivityAttendance> findByMemberAndActivityTypeAndAttendanceDate(
            Member member,
            ActivityType activityType,
            LocalDate attendanceDate
    );

    /*
     * 동일 회원, 활동 종류, 날짜의 출석 기록 삭제
     */
    void deleteByMemberAndActivityTypeAndAttendanceDate(
            Member member,
            ActivityType activityType,
            LocalDate attendanceDate
    );

    /*
     * 특정 회원의 출석 상태별 개수
     */
    long countByMemberAndStatus(
            Member member,
            AttendanceStatus status
    );

    /*
     * 전체 기간 회원별 출석 횟수 순위
     */
    @Query("""
            select count(a)
            from ActivityAttendance a
            where a.status =
                com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
              and a.member.active = true
            group by a.member
            order by count(a) desc
            """)
    List<Long> findAttendanceCountsByMember(Pageable pageable);

    /*
     * 선택한 회차의 회원별 전체 출석 횟수
     *
     * startDate 이상
     * endDate 이하
     */
    @Query("""
            select
                a.member.memberId as memberId,
                count(a) as attendanceCount
            from ActivityAttendance a
            where a.status =
                com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
              and a.member.active = true
              and (:startDate is null
                   or a.attendanceDate >= :startDate)
              and (:endDate is null
                   or a.attendanceDate <= :endDate)
            group by a.member.memberId
            """)
    List<MemberAttendanceCountProjection>
    findAttendanceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /*
     * 선택한 회차의 회원별, 활동 종류별 출석 횟수
     */
    @Query("""
            select
                a.member.memberId as memberId,
                a.activityType.activityTypeId as activityTypeId,
                count(a) as attendanceCount
            from ActivityAttendance a
            where a.status =
                com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
              and a.member.active = true
              and a.activityType.active = true
              and (:startDate is null
                   or a.attendanceDate >= :startDate)
              and (:endDate is null
                   or a.attendanceDate <= :endDate)
            group by
                a.member.memberId,
                a.activityType.activityTypeId
            """)
    List<MemberActivityAttendanceCountProjection>
    findMemberActivityCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /*
     * 선택한 회차의 패널티 적용 활동 출석 횟수
     *
     * BossParticipationRecord에 존재하고
     * attendanceApplied가 null 또는 true인 활동만 계산
     */
    @Query("""
            select
                a.member.memberId as memberId,
                a.activityType.activityTypeId as activityTypeId,
                count(a) as attendanceCount
            from ActivityAttendance a
            where a.status =
                com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
              and a.member.active = true
              and a.activityType.active = true
              and exists (
                  select 1
                  from BossParticipationRecord r
                  where r.activityType = a.activityType
                    and r.bossDate = a.attendanceDate
                    and (
                        r.attendanceApplied is null
                        or r.attendanceApplied = true
                    )
              )
              and (:startDate is null
                   or a.attendanceDate >= :startDate)
              and (:endDate is null
                   or a.attendanceDate <= :endDate)
            group by
                a.member.memberId,
                a.activityType.activityTypeId
            """)
    List<MemberActivityAttendanceCountProjection>
    findMemberPenaltyActivityCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /*
     * 선택한 회차의 활동 종류별 진행 일수
     *
     * 같은 날짜에 여러 명이 출석해도
     * 활동 발생 횟수는 날짜 기준으로 한 번만 계산
     */
    @Query("""
            select
                a.activityType.activityTypeId as activityTypeId,
                count(distinct a.attendanceDate) as totalCount
            from ActivityAttendance a
            where a.status =
                com.clanmanager.clanmanager.entity.AttendanceStatus.ATTENDED
              and a.activityType.active = true
              and (:startDate is null
                   or a.attendanceDate >= :startDate)
              and (:endDate is null
                   or a.attendanceDate <= :endDate)
            group by a.activityType.activityTypeId
            """)
    List<ActivityOccurrenceCountProjection>
    findActivityOccurrenceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /*
     * 회원별 전체 출석 횟수 Projection
     */
    interface MemberAttendanceCountProjection {

        Long getMemberId();

        Long getAttendanceCount();
    }

    /*
     * 회원별, 활동 종류별 출석 횟수 Projection
     */
    interface MemberActivityAttendanceCountProjection {

        Long getMemberId();

        Long getActivityTypeId();

        Long getAttendanceCount();
    }

    /*
     * 활동 종류별 발생 횟수 Projection
     */
    interface ActivityOccurrenceCountProjection {

        Long getActivityTypeId();

        Long getTotalCount();
    }
}
