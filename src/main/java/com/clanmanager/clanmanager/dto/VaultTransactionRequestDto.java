package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VaultTransactionRequestDto {

    private Long amountDiamonds;
    private Long balanceDiamonds;
    private Long targetMemberId;
    private Long createdByMemberId;
    private String memo;
}
