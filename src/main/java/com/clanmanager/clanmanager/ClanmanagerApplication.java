package com.clanmanager.clanmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClanmanagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClanmanagerApplication.class, args);
    }

}
