package com.chat.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatBackApplication.class, args);
    }
}
