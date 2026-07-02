package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.BossParticipationMember;
import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BossParticipationMemberRepository extends JpaRepository<BossParticipationMember, Long> {

    List<BossParticipationMember> findByRecordOrderByClanNameAscCharacterNameAsc(BossParticipationRecord record);

    long countByRecord(BossParticipationRecord record);

    void deleteByRecord(BossParticipationRecord record);
}
