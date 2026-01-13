package com.porcana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PorcanaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PorcanaApplication.class, args);
    }
}
