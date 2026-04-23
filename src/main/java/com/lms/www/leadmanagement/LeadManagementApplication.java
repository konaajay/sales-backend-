package com.lms.www.leadmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LeadManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadManagementApplication.class, args);
    }
}
