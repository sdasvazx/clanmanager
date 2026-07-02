package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.Notice;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NoticeResponseDto {

    private Long noticeId;
    private String title;
    private String content;
    private Long createdByMemberId;
    private String createdByName;
    private LocalDateTime createdAt;

    public static NoticeResponseDto from(Notice notice) {
        Member createdBy = notice.getCreatedBy();
        return NoticeResponseDto.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .createdByMemberId(createdBy == null ? null : createdBy.getMemberId())
                .createdByName(createdBy == null ? null : createdBy.getCharacterName())
                .createdAt(notice.getCreatedAt())
                .build();
    }
}
