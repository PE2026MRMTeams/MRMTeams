// COMMENTED BECAUSE OF TODO ENTITY REMOVAL!
// Left as an example, will be looked into in the future

//package ro.unibuc.prodeng.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import ro.unibuc.prodeng.IntegrationTestBase;
//import ro.unibuc.prodeng.repository.UserRepository;
//import ro.unibuc.prodeng.request.CreateUserRequest;
//
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@DisplayName("TodoController Integration Tests")
//class TodoControllerIntegrationTest extends IntegrationTestBase {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private TodoRepository todoRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @BeforeEach
//    void cleanUp() {
//        todoRepository.deleteAll();
//        userRepository.deleteAll();
//    }
//
//    private void createUser(String name, String email, String password, String role) throws Exception {
//        CreateUserRequest request = new CreateUserRequest(name, email, password, role);
//
//        mockMvc.perform(post("/api/users")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated());
//    }
//
//    private String createTodo(String description, String assigneeEmail) throws Exception {
//        CreateTodoRequest request = new CreateTodoRequest(description, assigneeEmail);
//
//        String response = mockMvc.perform(post("/api/todos")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.description").value(description))
//                .andExpect(jsonPath("$.done").value(false))
//                .andExpect(jsonPath("$.assigneeEmail").value(assigneeEmail))
//                .andExpect(jsonPath("$.id").exists())
//                .andReturn().getResponse().getContentAsString();
//
//        return objectMapper.readTree(response).get("id").asText();
//    }
//
//    @Test
//    void testCreateAndGetTodo_validTodoCreation_retrievesTodoSuccessfully() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        String todoId = createTodo("Buy milk", "alice@example.com");
//
//        // Act & Assert
//        mockMvc.perform(get("/api/todos/" + todoId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.description").value("Buy milk"))
//                .andExpect(jsonPath("$.done").value(false))
//                .andExpect(jsonPath("$.assigneeName").value("Alice"))
//                .andExpect(jsonPath("$.assigneeEmail").value("alice@example.com"));
//    }
//
//    @Test
//    void testGetTodosByUser_multipleUsersWithDifferentTodos_filtersCorrectly() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        createUser("Bob", "bob@example.com", "password2", "admin");
//        createTodo("Buy milk", "alice@example.com");
//        createTodo("Walk the dog", "alice@example.com");
//        createTodo("Clean house", "bob@example.com");
//
//        // Act & Assert
//        mockMvc.perform(get("/api/todos").param("assigneeEmail", "alice@example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(2));
//
//        mockMvc.perform(get("/api/todos").param("assigneeEmail", "bob@example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(1));
//    }
//
//    @Test
//    void testSetDone_toggleDoneStatus_updatesStatusCorrectly() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        String todoId = createTodo("Buy milk", "alice@example.com");
//
//        // Act & Assert
//        mockMvc.perform(patch("/api/todos/" + todoId + "/done")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("true"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.done").value(true));
//
//        mockMvc.perform(patch("/api/todos/" + todoId + "/done")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("false"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.done").value(false));
//    }
//
//    @Test
//    void testAssign_reassignToDifferentUser_updateAssigneeSuccessfully() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        createUser("Bob", "bob@example.com", "password2", "admin");
//        String todoId = createTodo("Buy milk", "alice@example.com");
//
//        // Act & Assert
//        mockMvc.perform(patch("/api/todos/" + todoId + "/assignee")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"newAssigneeEmail\":\"bob@example.com\"}"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.assigneeName").value("Bob"))
//                .andExpect(jsonPath("$.assigneeEmail").value("bob@example.com"));
//    }
//
//    @Test
//    void testEditDescription_validNewDescription_updatesDescriptionSuccessfully() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        String todoId = createTodo("Buy milk", "alice@example.com");
//
//        // Act & Assert
//        mockMvc.perform(patch("/api/todos/" + todoId + "/description")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"description\":\"Buy oat milk\"}"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.description").value("Buy oat milk"));
//    }
//
//    @Test
//    void testDeleteTodo_existingTodo_deletesSuccessfully() throws Exception {
//        // Arrange
//        createUser("Alice", "alice@example.com", "password", "admin");
//        String todoId = createTodo("Buy milk", "alice@example.com");
//
//        // Act & Assert
//        mockMvc.perform(delete("/api/todos/" + todoId))
//                .andExpect(status().isNoContent());
//
//        mockMvc.perform(get("/api/todos").param("assigneeEmail", "alice@example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(0));
//    }
//
//    @Test
//    void testGetTodoById_nonExistentTodo_returnsNotFound() throws Exception {
//        // Arrange
//        String nonExistentId = "nonexistent-todo-id";
//
//        // Act & Assert
//        mockMvc.perform(get("/api/todos/" + nonExistentId))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.error").value("Entity: " + nonExistentId + " was not found"));
//    }
//
//    @Test
//    void testCreateTodo_nonExistentAssignee_returnsNotFound() throws Exception {
//        // Arrange
//        CreateTodoRequest request = new CreateTodoRequest("Buy milk", "nonexistent@example.com");
//
//        // Act & Assert
//        mockMvc.perform(post("/api/todos")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.error").exists());
//    }
//
//    @Test
//    void testSetDone_nonExistentTodo_returnsNotFound() throws Exception {
//        // Arrange
//        String nonExistentId = "nonexistent-todo-id";
//
//        // Act & Assert
//        mockMvc.perform(patch("/api/todos/" + nonExistentId + "/done")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("true"))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.error").value("Entity: " + nonExistentId + " was not found"));
//    }
//}
