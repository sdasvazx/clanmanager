package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.BossParticipationMember;
import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BossParticipationMemberRepository extends JpaRepository<BossParticipationMember, Long> {

    List<BossParticipationMember> findByRecordOrderByClanNameAscCharacterNameAsc(BossParticipationRecord record);

    long countByRecord(BossParticipationRecord record);

    void deleteByRecord(BossParticipationRecord record);

    @Query("select bpm from BossParticipationMember bpm join fetch bpm.record r where bpm.member = :member order by r.bossDate desc, r.cutTime desc, r.createdAt desc")
    List<BossParticipationMember> findByMemberWithRecordOrderByRecent(@Param("member") Member member);
}
