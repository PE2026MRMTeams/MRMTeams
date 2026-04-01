package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ro.unibuc.prodeng.IntegrationTestBase;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.EditMessageRequest;
import ro.unibuc.prodeng.request.LoginRequest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MessageController Integration Tests")
class MessageControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // clean database before each test
    @BeforeEach
    void cleanUp() {
        messageRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    // shared test method to avoid repeating logic between tests
    private String createUser(String name, String email, String password, String role) throws Exception {
        CreateUserRequest request = new CreateUserRequest(name, email, password, role);

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    // shared test method to create a team
    private String loginUser(String email, String password) throws Exception {

        LoginRequest request = new LoginRequest(email, password);

        String response = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private String createTeam(String name, String token) throws Exception {
        String teamPayload = "{\"name\":\"" + name + "\"}";

        String response = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teamPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    // shared test method to create a message
    private String createMessage(String teamId, String content, String token) throws Exception {
        CreateMessageRequest request = new CreateMessageRequest(content);

        String response = mockMvc.perform(post("/api/teams/{teamId}/messages", teamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void testCreateAndGetMessage_validMessageCreation_retrievesMessageSuccessfully() throws Exception {
        // Arrange
        String userId = createUser("Alice", "alice@example.com", "password123", "admin");
        String token = loginUser("alice@example.com", "password123");
        String teamId = createTeam("Engineering Team", token);
        String messageId = createMessage(teamId, "Hello Team!", token);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", teamId, messageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello Team!"))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.sentBy").value(userId));
    }

    @Test
    void testEditMessage_validMessageUpdate_updatesMessageSuccessfully() throws Exception {
        // Arrange
        String userId = createUser("Alice", "alice@example.com", "password123", "admin");
        String token = loginUser("alice@example.com", "password123");
        String teamId = createTeam("Engineering Team", token);
        String messageId = createMessage(teamId, "Hello Team!", token);

        // Act & Assert
        EditMessageRequest editRequest = new EditMessageRequest("Updated message content");
        mockMvc.perform(put("/api/teams/{teamId}/messages/{messageId}", teamId, messageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated message content"))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.sentBy").value(userId));
    }

    @Test
    void testGetMessages_multipleMessagesExist_returnsAllMessages() throws Exception {
        // Arrange
        createUser("Alice", "alice@example.com", "password123", "admin");
        String token = loginUser("alice@example.com", "password123");
        String teamId = createTeam("Engineering Team", token);
        createMessage(teamId, "First message", token);
        createMessage(teamId, "Second message", token);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages", teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testEditMessage_blankContent_returnsBadRequest() throws Exception {
        // Arrange
        createUser("Alice", "alice@example.com", "password123", "admin");
        String token = loginUser("alice@example.com", "password123");
        String teamId = createTeam("Engineering Team", token);
        String messageId = createMessage(teamId, "Original message", token);

        // Act & Assert
        EditMessageRequest invalidRequest = new EditMessageRequest("   ");
        mockMvc.perform(put("/api/teams/{teamId}/messages/{messageId}", teamId, messageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteMessage_existingMessage_deletesSuccessfully() throws Exception {
        // Arrange
        createUser("Admin", "admin@example.com", "password123", "admin");
        String adminToken = loginUser("admin@example.com", "password123");
        String teamId = createTeam("Engineering Team", adminToken);
        String messageId = createMessage(teamId, "Message to delete", adminToken);

        // Act & Assert - delete the message as admin
        mockMvc.perform(delete("/api/teams/{teamId}/messages/{messageId}", teamId, messageId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify the message is deleted
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", teamId, messageId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateMessage_validMessageCreation_returnsCreatedMessage() throws Exception {
        // Arrange
        String userId = createUser("Alice", "alice@example.com", "password123", "admin");
        String token = loginUser("alice@example.com", "password123");
        String teamId = createTeam("Engineering Team", token);

        // Act & Assert
        CreateMessageRequest request = new CreateMessageRequest("Test message");
        mockMvc.perform(post("/api/teams/{teamId}/messages", teamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Test message"))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.sentBy").value(userId))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sentAt").exists());
    }
}

