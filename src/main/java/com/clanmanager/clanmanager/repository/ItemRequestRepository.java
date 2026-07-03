package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ItemRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRequestRepository extends JpaRepository<ItemRequest, Long> {

    List<ItemRequest> findAllByOrderByCreatedAtDesc();
}
