package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.CollectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionHistoryRepository extends JpaRepository<CollectionHistory, Long> {

    List<CollectionHistory> findTop80ByOrderByCreatedAtDesc();
}
