package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.BossParticipationMember;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BossParticipationMemberDto {

    private Long participationMemberId;
    private Long memberId;
    private String characterName;
    private String clanName;
    private Boolean matched;

    public static BossParticipationMemberDto from(BossParticipationMember member) {
        return BossParticipationMemberDto.builder()
                .participationMemberId(member.getParticipationMemberId())
                .memberId(member.getMember() == null ? null : member.getMember().getMemberId())
                .characterName(member.getCharacterName())
                .clanName(member.getClanName())
                .matched(member.getMatched())
                .build();
    }
}
