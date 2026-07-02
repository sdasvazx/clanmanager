package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BossParticipationRecordRepository extends JpaRepository<BossParticipationRecord, Long> {

    List<BossParticipationRecord> findAllByOrderByBossDateDescCutTimeDescCreatedAtDesc();
}
