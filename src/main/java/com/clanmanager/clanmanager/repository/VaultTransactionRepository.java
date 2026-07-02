package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VaultTransactionRepository extends JpaRepository<VaultTransaction, Long> {

    List<VaultTransaction> findTop50ByOrderByCreatedAtDesc();

    long countByType(VaultTransactionType type);
}
