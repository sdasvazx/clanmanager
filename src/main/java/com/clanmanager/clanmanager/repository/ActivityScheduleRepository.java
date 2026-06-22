package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivitySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityScheduleRepository extends JpaRepository<ActivitySchedule, Long> {
}