package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByCharacterName(String characterName);

    boolean existsByCharacterName(String characterName);
}