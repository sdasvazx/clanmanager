package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boss_participation_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BossParticipationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participationMemberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private BossParticipationRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 50)
    private String characterName;

    @Column(nullable = false, length = 30)
    private String clanName;

    @Column(nullable = false)
    private Boolean matched;
}
