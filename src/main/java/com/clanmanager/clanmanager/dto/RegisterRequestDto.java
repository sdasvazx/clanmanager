package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDto {

    private String characterName;
    private String password;
    private Integer combatPower;
}