package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {

    @Query("select i from CollectionItem i where i.active = true order by i.sortOrder asc, i.collectionItemId asc")
    List<CollectionItem> findActiveItems();

    Optional<CollectionItem> findByItemName(String itemName);

    boolean existsByItemName(String itemName);

    long countByActiveTrue();
}
