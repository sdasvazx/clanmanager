package com.clanmanager.clanmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @NotBlank(message = "캐릭터 이름을 입력해 주세요.")
    @Size(max = 50, message = "캐릭터 이름은 50자 이하로 입력해 주세요.")
    private String characterName;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = 100, message = "비밀번호는 100자 이하로 입력해 주세요.")
    private String password;
}
