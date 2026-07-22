package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.DistributionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DistributionSnapshotRepository extends JpaRepository<DistributionSnapshot, Long> {

    List<DistributionSnapshot> findTop50ByOrderByCreatedAtDesc();

    Optional<DistributionSnapshot> findTopByOrderByCreatedAtDesc();
}
