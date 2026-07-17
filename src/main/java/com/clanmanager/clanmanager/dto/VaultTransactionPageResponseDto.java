package com.clanmanager.clanmanager.dto;

import java.util.List;

public record VaultTransactionPageResponseDto(
        List<VaultTransactionResponseDto> transactions,
        int page,
        int pageSize,
        int totalPages,
        long totalElements
) {
}
