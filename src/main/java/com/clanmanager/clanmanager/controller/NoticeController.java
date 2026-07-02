package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.NoticeRequestDto;
import com.clanmanager.clanmanager.dto.NoticeResponseDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.Notice;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public List<NoticeResponseDto> getNotices() {
        return noticeRepository.findByVisibleTrueOrderByCreatedAtDesc().stream()
                .map(NoticeResponseDto::from)
                .toList();
    }

    @PostMapping
    public NoticeResponseDto createNotice(@RequestBody NoticeRequestDto request) {
        Member createdBy = memberRepository.findById(request.getCreatedByMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
        if (createdBy.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 공지사항을 등록할 수 있습니다.");
        }

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (title.isBlank() || content.isBlank()) {
            throw new IllegalArgumentException("공지 제목과 내용을 모두 입력해 주세요.");
        }

        Notice saved = noticeRepository.save(Notice.builder()
                .title(title)
                .content(content)
                .createdBy(createdBy)
                .visible(true)
                .build());
        return NoticeResponseDto.from(saved);
    }
}
