package com.clanmanager.clanmanager.dto;

import java.util.List;

public record BossParticipationPageResponseDto(
        List<BossParticipationResponseDto> records,
        int page,
        int pageSize,
        int totalPages,
        long totalElements
) {
}
