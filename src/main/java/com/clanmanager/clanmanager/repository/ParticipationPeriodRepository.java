package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ParticipationPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipationPeriodRepository extends JpaRepository<ParticipationPeriod, Long> {

    Optional<ParticipationPeriod> findByPeriodIndex(Integer periodIndex);

    List<ParticipationPeriod> findAllByOrderByPeriodIndexAsc();
}
