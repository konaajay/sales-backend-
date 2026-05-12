package com.lms.www.leadmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
public class LeadManagementApplication {

    public static void main(String[] args) {
        System.out.println("RELOADING: HISTORICAL TARGET ENGINE ACTIVATED...");
        SpringApplication.run(LeadManagementApplication.class, args);
    }
}
