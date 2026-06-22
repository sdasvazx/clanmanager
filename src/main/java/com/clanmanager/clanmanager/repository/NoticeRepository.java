package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByVisibleTrueOrderByCreatedAtDesc();
}