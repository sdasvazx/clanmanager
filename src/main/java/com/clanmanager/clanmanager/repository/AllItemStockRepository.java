package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.AllItemStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllItemStockRepository extends JpaRepository<AllItemStock, Long> {
    List<AllItemStock> findAllByOrderByDisplayOrderAscAllItemStockIdAsc();
}
