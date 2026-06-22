package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {
}