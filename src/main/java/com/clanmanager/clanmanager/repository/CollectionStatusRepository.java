package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.CollectionItem;
import com.clanmanager.clanmanager.entity.CollectionStatus;
import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionStatusRepository extends JpaRepository<CollectionStatus, Long> {

    List<CollectionStatus> findAllByOrderByUpdatedAtDesc();

    Optional<CollectionStatus> findByMemberAndItem(Member member, CollectionItem item);
}
