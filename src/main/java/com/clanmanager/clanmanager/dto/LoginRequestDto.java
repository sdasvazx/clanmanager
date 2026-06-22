package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    private String characterName;
    private String password;
}