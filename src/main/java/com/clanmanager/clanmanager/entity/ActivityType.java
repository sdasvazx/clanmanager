package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityTypeId;

    @Column(nullable = false, length = 50)
    private String typeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityCategory category;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    public void prePersist() {
        this.score = this.score == null ? 1 : this.score;
        this.active = this.active == null ? true : this.active;
    }
}