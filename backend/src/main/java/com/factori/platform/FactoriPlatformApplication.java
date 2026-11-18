package com.factori.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class FactoriPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(FactoriPlatformApplication.class, args);
    }
}
