package tn.esprit.authentificationmicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tn.esprit.authentificationmicroservice.Entity.Enum.Role;
import tn.esprit.authentificationmicroservice.Entity.User;
import tn.esprit.authentificationmicroservice.Repository.UserRepository;

@SpringBootApplication
@EnableDiscoveryClient
public class AuthentificationMicroServiceApplication implements CommandLineRunner {

	@Autowired
	private UserRepository userRepository;

	public static void main(String[] args) {
		SpringApplication.run(AuthentificationMicroServiceApplication.class, args);
	}
	@Override
	public void run(String... args) throws Exception {
		User adminAccount = userRepository.findByRole(Role.ADMIN);
		if (adminAccount == null) {
			User user = new User();
			user.setEmail("admin@admin.com");
			user.setNom("admin");
			user.setPrenom("admin");
			user.setTelephone("12345678");
			user.setAdresse("admin");
			user.setPassword(new BCryptPasswordEncoder().encode("admin"));
			user.setRole(Role.ADMIN);
			userRepository.save(user);
		}
	}


	}



