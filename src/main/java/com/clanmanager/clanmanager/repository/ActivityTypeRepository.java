package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {

    boolean existsByTypeName(String typeName);

    Optional<ActivityType> findByTypeName(String typeName);

    List<ActivityType> findByActiveTrueOrderByActivityTypeIdAsc();

    List<ActivityType> findByActiveTrueOrderByDisplayOrderAscActivityTypeIdAsc();

    List<ActivityType> findAllByOrderByDisplayOrderAscActivityTypeIdAsc();
}
