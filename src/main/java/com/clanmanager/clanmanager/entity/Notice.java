package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noticeId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String imageDataUrl;

    @Column(length = 255)
    private String imageFileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @Column(nullable = false)
    private Boolean visible;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.visible = this.visible == null ? true : this.visible;
        this.createdAt = LocalDateTime.now();
    }
}
