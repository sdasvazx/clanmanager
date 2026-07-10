package com.clanmanager.clanmanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VaultTransactionRequestDto {

    @Min(value = 1, message = "다이아 수량은 1 이상으로 입력해 주세요.")
    private Long amountDiamonds;

    @Min(value = 0, message = "금고 잔액은 0 이상으로 입력해 주세요.")
    private Long balanceDiamonds;
    private Long targetMemberId;
    private Long createdByMemberId;

    @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
    private String memo;
}
