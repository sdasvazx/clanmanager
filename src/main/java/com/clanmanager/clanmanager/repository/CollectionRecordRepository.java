package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.CollectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRecordRepository extends JpaRepository<CollectionRecord, Long> {
    List<CollectionRecord> findAllByOrderByCreatedAtDesc();
}
