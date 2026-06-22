package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.Notice;
import com.clanmanager.clanmanager.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;

    @GetMapping
    public List<Notice> getNotices() {
        return noticeRepository.findByVisibleTrueOrderByCreatedAtDesc();
    }

    @PostMapping
    public Notice createNotice(@RequestBody Notice notice) {
        return noticeRepository.save(notice);
    }
}