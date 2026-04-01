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

import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.repository.FolderRepository;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;


//No forms, respect my logic

//No forms, respect my logic
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class })
@EnableMongoRepositories
public class ProdEngApplication {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

        @Autowired
        private TeamRepository teamRepository;

        @Autowired
        private FolderRepository folderRepository;

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


	//REQUESTS FOR PERFORMANCE TESTING---Folders

	// Admin
        if (userRepository.findByEmail("andrei.echipa@prodeng.ro").isEmpty()) {
                    userService.createUser(new CreateUserRequest("Andrei Telu", "andrei.echipa@prodeng.ro", "password", "admin"));
        }
        UserEntity andrei = userRepository.findByEmail("andrei.echipa@prodeng.ro").get();

        

        //2xTeam
        TeamEntity team1 = teamRepository.findAll().stream()
                .filter(t -> "Andrei Team1".equals(t.name()))
                .findFirst()
                .orElseGet(() -> teamRepository.save(new TeamEntity(
                        "team1", "Andrei Team1", "Teams for performance testing", andrei.id(), List.of(andrei.id()), Instant.now()
                )));

        teamRepository.findAll().stream()
                .filter(t -> "Andrei Team2".equals(t.name()))
                .findFirst()
                .orElseGet(() -> teamRepository.save(new TeamEntity(
                        "team2", "Andrei Team2", "Teams for cleaning the data", andrei.id(), List.of(andrei.id()), Instant.now()
                )));

        //ROOT FOLDERS for Team 1
        FolderEntity root1 = folderRepository.findByTeamId(team1.id()).stream()
                .filter(f -> "Root Folder1".equals(f.name()) && f.parentFolderId() == null)
                .findFirst()
                .orElseGet(() -> folderRepository.save(new FolderEntity(
                        "rootfolder1", "Root Folder1", team1.id(), null, andrei.id(), Instant.now(), Instant.now()
                )));

        folderRepository.findByTeamId(team1.id()).stream()
                .filter(f -> "Root Folder2".equals(f.name()) && f.parentFolderId() == null)
                .findFirst()
                .orElseGet(() -> folderRepository.save(new FolderEntity(
                        "rootfolder2", "Root Folder2", team1.id(), null, andrei.id(), Instant.now(), Instant.now()
                )));

        // SUBFOLDER
        folderRepository.findByParentFolderId(root1.id()).stream()
                .filter(f -> "Subfolder1".equals(f.name()))
                .findFirst()
                .orElseGet(() -> folderRepository.save(new FolderEntity(
                        "subfolder1", "Subfolder1", team1.id(), root1.id(), andrei.id(), Instant.now(), Instant.now()
                )));
	}
}
