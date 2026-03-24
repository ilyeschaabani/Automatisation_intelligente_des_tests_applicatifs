package tn.esprit.examen.nomPrenomClasseExamen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


// Permet au service de s'enregistrer dans Eureka
@SpringBootApplication
@EnableDiscoveryClient
public class nomPrenomClasseExamenApplication {

    public static void main(String[] args) {
        SpringApplication.run(nomPrenomClasseExamenApplication.class, args);
    }
}
