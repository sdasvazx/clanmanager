package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.NoticeRequestDto;
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
    public List<Notice> getNotices() {
        return noticeRepository.findByVisibleTrueOrderByCreatedAtDesc();
    }

    @PostMapping
    public Notice createNotice(@RequestBody NoticeRequestDto request) {
        Member createdBy = memberRepository.findById(request.getCreatedByMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
        if (createdBy.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 공지사항을 등록할 수 있습니다.");
        }
        return noticeRepository.save(Notice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .createdBy(createdBy)
                .visible(true)
                .build());
    }
}
