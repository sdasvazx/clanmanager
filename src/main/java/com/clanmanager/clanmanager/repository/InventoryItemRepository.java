package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findAllByOrderByCreatedAtDesc();
}
