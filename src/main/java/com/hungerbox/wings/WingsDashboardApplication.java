package com.hungerbox.wings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WingsDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(WingsDashboardApplication.class, args);
    }
}
