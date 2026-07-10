package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByCharacterName(String characterName);

    boolean existsByCharacterName(String characterName);

    boolean existsByCharacterNameAndMemberIdNot(String characterName, Long memberId);

    boolean existsByRole(MemberRole role);

    long countByRole(MemberRole role);

    Optional<Member> findFirstByOrderByMemberIdAsc();

    Optional<Member> findFirstByActiveTrueOrderByMemberIdAsc();

    List<Member> findByCharacterNameContaining(String keyword);

    List<Member> findByActiveTrueOrderByMemberIdAsc();
}
