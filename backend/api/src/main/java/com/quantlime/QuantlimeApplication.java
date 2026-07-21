package com.quantlime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.quantlime")
public class QuantlimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantlimeApplication.class, args);
    }
}
