package ro.unibuc.prodeng;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.service.UserService;

import jakarta.annotation.PostConstruct;


import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
//No forms, respect my logic
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class })
@EnableMongoRepositories
public class ProdEngApplication {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	public static void main(String[] args) {
		SpringApplication.run(ProdEngApplication.class, args);
	}

	@PostConstruct
	public void runAfterObjectCreated() {
		if (userRepository.findByEmail("frodo@theshire.me").isEmpty()) {
			CreateUserRequest userRequest = new CreateUserRequest("Frodo Baggins", "frodo@theshire.me", "password", "user");
			userService.createUser(userRequest);
		}
		if (userRepository.findByEmail("mihait@gmail.com").isEmpty()) {
			CreateUserRequest userRequest = new CreateUserRequest("Telu Mihai", "mihait@gmail.com", "password", "admin");
			userService.createUser(userRequest);
		}
	}
}
