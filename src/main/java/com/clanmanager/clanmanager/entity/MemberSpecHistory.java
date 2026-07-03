package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_spec_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSpecHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberSpecHistoryId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String characterName;

    private Integer previousCombatPower;
    private Integer nextCombatPower;
    private Integer previousLevel;
    private Integer nextLevel;

    @Column(length = 30)
    private String previousGuildName;

    @Column(length = 30)
    private String nextGuildName;

    @Column(length = 50)
    private String previousCharacterClass;

    @Column(length = 50)
    private String nextCharacterClass;

    @Column(length = 30)
    private String previousRank;

    @Column(length = 30)
    private String nextRank;

    @Column(length = 30)
    private String previousStatus;

    @Column(length = 30)
    private String nextStatus;

    private Long editedByMemberId;

    @Column(length = 50)
    private String editedByName;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
