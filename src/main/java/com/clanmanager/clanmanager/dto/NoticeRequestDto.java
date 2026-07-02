package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeRequestDto {

    private String title;
    private String content;
    private Long createdByMemberId;
}
