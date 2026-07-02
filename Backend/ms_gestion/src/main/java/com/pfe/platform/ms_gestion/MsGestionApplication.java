package com.pfe.platform.ms_gestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MsGestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsGestionApplication.class, args);
	}

}
