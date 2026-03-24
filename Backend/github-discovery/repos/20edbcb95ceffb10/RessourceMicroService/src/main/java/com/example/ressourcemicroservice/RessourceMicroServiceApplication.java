package com.example.ressourcemicroservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient

public class RessourceMicroServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RessourceMicroServiceApplication.class, args);
	}

}
