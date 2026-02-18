package com.pfe.platform.eurekaserveur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServeurApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServeurApplication.class, args);
    }

}
