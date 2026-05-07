package com.pfe.platform.msexecution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MsExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsExecutionApplication.class, args);
    }

}
