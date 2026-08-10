package com.focusos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class FocusOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FocusOsApplication.class, args);
    }
}
