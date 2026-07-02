package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ItemBid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemBidRepository extends JpaRepository<ItemBid, Long> {
    List<ItemBid> findAllByOrderByCreatedAtDesc();
}
