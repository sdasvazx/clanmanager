package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BossParticipationRecordRepository
        extends JpaRepository<BossParticipationRecord, Long> {

    List<BossParticipationRecord> findAllByOrderByBossDateDescCutTimeDescCreatedAtDesc();

    Page<BossParticipationRecord> findAllByOrderByBossDateDescCutTimeDescCreatedAtDesc(Pageable pageable);

    @Query("""
            select
                r.activityType.activityTypeId as activityTypeId,
                count(distinct r.bossDate) as totalCount
            from BossParticipationRecord r
            where r.activityType is not null
              and r.activityType.active = true
              and (
                  r.attendanceApplied is null
                  or r.attendanceApplied = true
              )
              and (
                  :startDate is null
                  or r.bossDate >= :startDate
              )
              and (
                  :endDate is null
                  or r.bossDate <= :endDate
              )
            group by r.activityType.activityTypeId
            """)
    List<ActivityOccurrenceCountProjection>
    findAppliedActivityOccurrenceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select
                r.activityType.activityTypeId as activityTypeId,
                count(distinct r.bossDate) as totalCount
            from BossParticipationRecord r
            where r.activityType is not null
              and r.activityType.active = true
              and (
                  r.attendanceApplied is null
                  or r.attendanceApplied = true
              )
              and (
                  :startDate is null
                  or r.bossDate >= :startDate
              )
              and (
                  :endDate is null
                  or r.bossDate <= :endDate
              )
            group by r.activityType.activityTypeId
            """)
    List<ActivityOccurrenceCountProjection>
    findPenaltyActivityOccurrenceCountsByPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    interface ActivityOccurrenceCountProjection {

        Long getActivityTypeId();

        Long getTotalCount();
    }
}
