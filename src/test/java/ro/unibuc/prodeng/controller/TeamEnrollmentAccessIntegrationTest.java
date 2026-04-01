package ro.unibuc.prodeng.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ro.unibuc.prodeng.IntegrationTestBase;
import ro.unibuc.prodeng.repository.TeamJoinRequestRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateTeamRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.LoginRequest;

@DisplayName("Team Enrollment and Access Control Integration Tests")
@SuppressWarnings("null")
class TeamEnrollmentAccessIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeamJoinRequestRepository teamJoinRequestRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        teamJoinRequestRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    //401 access without token
    @Test
    void testCreateTeam_withoutToken_returnsUnauthorized() throws Exception {
        CreateTeamRequest request = new CreateTeamRequest("Platform Team", "Core platform team");

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    //403 when non-admin tries team creation
    @Test
    void testCreateTeam_withNonAdminToken_returnsForbidden() throws Exception {
        createUser("User One", "user1@example.com", "password123", "user");
        String userToken = login("user1@example.com", "password123");
        CreateTeamRequest request = new CreateTeamRequest("Data Team", "Data workloads");

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
    
    //duplicate join request returns 400
    @Test
    void testRequestToJoinTeam_duplicatePendingRequest_returnsBadRequest() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String teamId = createTeam(adminToken, "Comms Team", "Team chat");

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();

        // verify that one pending join request is persisted in DB
        assertTrue(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, userId));
        // verify there is exactly one join request row for this team
        assertEquals(1, teamJoinRequestRepository.findByTeamId(teamId).size());

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());

        // verify duplicate submission did not create a second DB entry
        assertEquals(1, teamJoinRequestRepository.findByTeamId(teamId).size());
    }

    //403 when non-admin tries to view join requests, but admin can view them
    @Test
    void testGetJoinRequests_nonAdminForbidden_adminCanReadRequests() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();
        String teamId = createTeam(adminToken, "Backend Team", "API owners");

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(userId));
    }

    //403 when non-admin tries to approve join request, but admin can approve them
    @Test
    void testApproveJoinRequest_nonAdminForbidden() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();
        String teamId = createTeam(adminToken, "Security Team", "Security owners");

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/teams/{teamId}/join-requests/{userId}/approve", teamId, userId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    //200 when admin approves join request and user can access team details after approval
    @Test
    void testTeamAccess_forUserBeforeAndAfterApproval() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();
        String teamId = createTeam(adminToken, "Infra Team", "Infrastructure");

        mockMvc.perform(get("/api/teams/{teamId}", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // verify pending join request exists before approval
        assertTrue(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, userId));

        mockMvc.perform(post("/api/teams/{teamId}/join-requests/{userId}/approve", teamId, userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // verify join request is removed from DB after approval
        assertFalse(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, userId));
        // verify approved user is persisted as a team member
        assertTrue(teamRepository.findById(teamId).orElseThrow().members().contains(userId));

        mockMvc.perform(get("/api/teams/{teamId}", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teamId));
    }

    //204 when admin rejects a join request and the pending request is deleted from DB
    @Test
    void testRejectJoinRequest_removesPendingRequestFromDatabase() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();
        String teamId = createTeam(adminToken, "Ops Team", "Operations");

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // verify pending join request exists before reject
        assertTrue(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, userId));

        mockMvc.perform(delete("/api/teams/{teamId}/join-requests/{userId}", teamId, userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // verify pending join request was deleted from DB
        assertFalse(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, userId));
    }

    //200 when admin removes a member and membership is persisted as removed
    @Test
    void testRemoveMember_updatesMembersInDatabase() throws Exception {
        createUser("Admin", "admin@example.com", "password123", "admin");
        createUser("User One", "user1@example.com", "password123", "user");

        String adminToken = login("admin@example.com", "password123");
        String userToken = login("user1@example.com", "password123");
        String userId = userRepository.findByEmail("user1@example.com").orElseThrow().id();
        String teamId = createTeam(adminToken, "QA Team", "Quality assurance");

        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/teams/{teamId}/join-requests/{userId}/approve", teamId, userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // verify member exists in team before removal
        assertTrue(teamRepository.findById(teamId).orElseThrow().members().contains(userId));

        mockMvc.perform(delete("/api/teams/{teamId}/members/{userId}", teamId, userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // verify member is no longer present in DB after removal
        assertFalse(teamRepository.findById(teamId).orElseThrow().members().contains(userId));
    }

    //helper methods for user creation, login, and team creation
    private void createUser(String name, String email, String password, String role) throws Exception {
        CreateUserRequest request = new CreateUserRequest(name, email, password, role);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String createTeam(String adminToken, String name, String description) throws Exception {
        CreateTeamRequest request = new CreateTeamRequest(name, description);

        MvcResult result = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }
}
