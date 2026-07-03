package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.MemberSpecHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberSpecHistoryRepository extends JpaRepository<MemberSpecHistory, Long> {

    List<MemberSpecHistory> findTop100ByOrderByCreatedAtDesc();

    List<MemberSpecHistory> findTop5ByMemberIdOrderByCreatedAtDesc(Long memberId);
}
