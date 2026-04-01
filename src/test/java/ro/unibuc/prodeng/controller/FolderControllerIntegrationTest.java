package ro.unibuc.prodeng.controller;


import com.fasterxml.jackson.core.type.TypeReference;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateTeamRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.CreateFolderRequest;
import ro.unibuc.prodeng.service.AuthContextService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ro.unibuc.prodeng.IntegrationTestBase;
import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.FolderRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.response.FolderResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("FolderController Integration Tests")
class FolderControllerIntegrationTest extends IntegrationTestBase {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("deprecation")
    @MockBean
    private AuthContextService authContextService;

    private static int nrFoldersCreated = 0;

    @BeforeEach
    void cleanUp() {
        folderRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }


    private String createUser(String name, String email, String password, String role) throws Exception {
        if (userRepository.findByEmail(email).isPresent()) {
            return userRepository.findByEmail(email).get().id();
        }
        CreateUserRequest request = new CreateUserRequest(name, email, password, role);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // ACT & ASSERT HTTP
        String response = null;
        try {            response = mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value(name))
                    .andExpect(jsonPath("$.email").value(email))
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // ASSERT DB
        List<UserEntity> users = userRepository.findAll();
        assertEquals(1, users.size(), "One user should be saved in the DB!");
        UserEntity user = users.get(0);
        assertEquals(name, user.name());
        assertEquals(email, user.email());
        assertEquals(role, user.role());
        assertEquals(true, encoder.matches(request.password(), user.password()), "Password should be encrypted and match the original!");
        
        return objectMapper.readTree(response).get("id").asText();
    }


    private String createTeam(String userId, String teamName, String description)throws Exception {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found!"));

        
        var existingTeam = teamRepository.findAll().stream()
                .filter(t -> t.name().equals(teamName))
                .findFirst();

        if (existingTeam.isPresent()) {
            return existingTeam.get().id();
        }

        CreateTeamRequest request = new CreateTeamRequest(teamName, description);

        when(authContextService.getCurrentUserFromToken()).thenReturn(user);

        //ACT & ASSERT HTTP
        String response = null;
        try {            response = mockMvc.perform(post("/api/teams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value(teamName))
                    .andExpect(jsonPath("$.description").value(description))
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // ASSERT DB
        List<TeamEntity> teams = teamRepository.findAll();
        assertEquals(1, teams.size(), "One team should be saved in the DB!");
        TeamEntity savedTeam = teams.get(0);
        assertEquals(teamName, savedTeam.name());
        assertEquals(description, savedTeam.description());
        assertEquals(userId, savedTeam.createdBy());
        assertEquals(new java.util.ArrayList<>(java.util.Collections.singletonList(user.id())), savedTeam.members());

        return objectMapper.readTree(response).get("id").asText();
    }

    private String testCreateFolder(String folderName, String parentFolderId) throws Exception {
        String userId = createUser("Admin", "admin@test.ro", "password", "admin");
        String teamId = createTeam(userId, "Team1", "Description1");

        String teamName = teamRepository.findById(teamId).orElseThrow(() -> new RuntimeException("Team not found!")).name();
        String parentFolderName = parentFolderId != null ? folderRepository.findById(parentFolderId).orElseThrow(() -> new RuntimeException("Parent folder not found!")).name() : null;

        CreateFolderRequest request = new CreateFolderRequest(folderName, teamId, parentFolderId);
        
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found!"));
        //Simulate that the authenticated user is the user we just created, who is also the team creator (and thus has admin rights on the team).
        when(authContextService.getCurrentUserFromToken()).thenReturn(user);

        // ASSERT DB
        assertEquals(nrFoldersCreated, folderRepository.count());

        // ACT & ASSERT HTTP
        String response = null;
        try {            
            var mvcResult = mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful()) 
                .andExpect(jsonPath("$.name").value(folderName))
                .andExpect(jsonPath("$.teamName").value(teamName));
            
            if (parentFolderName != null) {
                mvcResult.andExpect(jsonPath("$.parentFolderName").value(parentFolderName));
            }
            
            response = mvcResult.andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        nrFoldersCreated++;

        // ASSERT DB
        assertEquals(nrFoldersCreated, folderRepository.count(), nrFoldersCreated + " folders should be saved!");
        FolderEntity savedFolder = folderRepository.findAll().getLast();
        assertEquals(folderName, savedFolder.name());
        assertEquals(teamId, savedFolder.teamId());
        if (parentFolderId != null) {
                assertEquals(parentFolderId, savedFolder.parentFolderId());
        } else {
                assertEquals(null, savedFolder.parentFolderId());
        }

        return objectMapper.readTree(response).get("id").asText();
    }



    @Test
    void testCreateAndGetFoldersForATeam_validRequest_returnsCreated() throws Exception {
        String rootFolderId = testCreateFolder("Root Folder", null);
        String subFolderId1 = testCreateFolder("SubFolder", rootFolderId);
        String subFolderId2 = testCreateFolder("SubFolder2", rootFolderId);

        //Asert DB:
        List<FolderEntity> folders = folderRepository.findAll();
        assertEquals(3, folders.size(), "3 folders should be saved in the DB!");
        String teamId = folders.get(0).teamId();
        assertEquals(teamId, folders.get(1).teamId());
        assertEquals(teamId, folders.get(2).teamId());

        //Act & Assert HTTP:
        String response = null;
        try {   
            response = mockMvc.perform(get("/api/folders/{teamId}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //Asert DB:
        List<FolderEntity> foldersFromResponse = folderRepository.findByTeamId(teamId);
        assertEquals(3, foldersFromResponse.size(), "3 folders should be returned from the API!");
        assertEquals(teamId, foldersFromResponse.get(0).teamId());
        assertEquals(teamId, foldersFromResponse.get(1).teamId());
        assertEquals(teamId, foldersFromResponse.get(2).teamId());
        assertEquals(rootFolderId, foldersFromResponse.get(0).id());
        assertEquals(subFolderId1, foldersFromResponse.get(1).id());
        assertEquals(subFolderId2, foldersFromResponse.get(2).id());
        assertEquals(null, foldersFromResponse.get(0).parentFolderId());
        assertEquals(rootFolderId, foldersFromResponse.get(1).parentFolderId());
        assertEquals(rootFolderId, foldersFromResponse.get(2).parentFolderId());
        assertEquals("Root Folder", foldersFromResponse.get(0).name());
        assertEquals("SubFolder", foldersFromResponse.get(1).name());
        assertEquals("SubFolder2", foldersFromResponse.get(2).name());
        String userId = userRepository.findAll().get(0).id();
        assertEquals(userId, foldersFromResponse.get(0).createdBy());
        assertEquals(userId, foldersFromResponse.get(1).createdBy());
        assertEquals(userId, foldersFromResponse.get(2).createdBy());
    }

}