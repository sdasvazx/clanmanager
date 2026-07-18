package com.clanmanager.clanmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeRequestDto {

    @NotBlank(message = "공지 제목을 입력해 주세요.")
    @Size(max = 100, message = "공지 제목은 100자 이하로 입력해 주세요.")
    private String title;

    @NotBlank(message = "공지 내용을 입력해 주세요.")
    @Size(max = 10000, message = "공지 내용은 10000자 이하로 입력해 주세요.")
    private String content;

    @Size(max = 6000000, message = "공지 이미지는 4MB 이하로 등록해 주세요.")
    private String imageDataUrl;

    @Size(max = 255, message = "이미지 파일명은 255자 이하로 입력해 주세요.")
    private String imageFileName;

    @NotNull(message = "작성자 정보가 필요합니다.")
    private Long createdByMemberId;
}
