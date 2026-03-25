package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.request.ChangeNameRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.LoginRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
class UserControllerTest {
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private UserController userController;
    
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();
    
    private UserResponse testUser1 = new UserResponse("1", "John Doe", "john@example.com");
    private UserResponse testUser2 = new UserResponse("2", "Jane Smith", "jane@example.com");
    private CreateUserRequest createUserRequest = new CreateUserRequest("John Doe", "john@example.com", "password", "user");
    private ChangeNameRequest changeNameRequest = new ChangeNameRequest("John Updated");
    private LoginRequest loginRequest = new LoginRequest("john@example.com", "password");
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testGetAllUsers_withMultipleUsers_returnsListOfUsers() throws Exception {
        // Arrange
        List<UserResponse> users = Arrays.asList(testUser1, testUser2);
        when(userService.getAllUsers()).thenReturn(users);
        
        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].name", is("John Doe")))
                .andExpect(jsonPath("$[0].email", is("john@example.com")))
                .andExpect(jsonPath("$[1].id", is("2")))
                .andExpect(jsonPath("$[1].name", is("Jane Smith")))
                .andExpect(jsonPath("$[1].email", is("jane@example.com")));
        
        verify(userService, times(1)).getAllUsers();
    }
    
    @Test
    void testGetAllUsers_withNoUsers_returnsEmptyList() throws Exception {
        // Arrange
        when(userService.getAllUsers()).thenReturn(Arrays.asList());
        
        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        
        verify(userService, times(1)).getAllUsers();
    }
    
    @Test
    void testGetUserById_existingUserRequested_returnsUser() throws Exception {
        // Arrange
        String userId = "1";
        when(userService.getUserById(userId)).thenReturn(testUser1);
        
        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));
        
        verify(userService, times(1)).getUserById(userId);
    }
    
    @Test
    void testGetUserById_nonExistingUserRequested_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        when(userService.getUserById(userId)).thenThrow(new EntityNotFoundException("User"));
        
        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        
        verify(userService, times(1)).getUserById(userId);
    }
    
    @Test
    void testCreateUser_validRequestProvided_createsAndReturnsUser() throws Exception {
        // Arrange
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(testUser1);
        
        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));
        
        verify(userService, times(1)).createUser(any(CreateUserRequest.class));
    }
    
    @Test
    void testUpdateUser_existingUserRequested_updatesAndReturnsUser() throws Exception {
        // Arrange
        String userId = "1";
        UserResponse updatedUser = new UserResponse("1", "John Updated", "john@example.com");
        when(userService.changeName(eq(userId), eq("John Updated"))).thenReturn(updatedUser);
        
        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeNameRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Updated")))
                .andExpect(jsonPath("$.email", is("john@example.com")));
        
        verify(userService, times(1)).changeName(userId, "John Updated");
    }
    
    @Test
    void testUpdateUser_nonExistingUserRequested_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        when(userService.changeName(eq(userId), anyString()))
                .thenThrow(new EntityNotFoundException("User"));
        
        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeNameRequest)))
                .andExpect(status().isNotFound());
        
        verify(userService, times(1)).changeName(eq(userId), anyString());
    }

    @Test
    void testChangeName_existingUserRequested_updatesAndReturnsUser() throws Exception {
        // Arrange
        String userId = "1";
        UserResponse updatedUser = new UserResponse("1", "John Updated", "john@example.com");
        when(userService.changeName(eq(userId), eq("John Updated"))).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(patch("/api/users/{id}/name", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeNameRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Updated")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).changeName(userId, "John Updated");
    }

    @Test
    void testChangeName_withBlankName_returnsBadRequest() throws Exception {
        // Arrange
        ChangeNameRequest invalidRequest = new ChangeNameRequest("   ");

        // Act & Assert
        mockMvc.perform(patch("/api/users/{id}/name", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changeName(anyString(), anyString());
    }

    @Test
    void testDeleteUser_existingUserRequested_returnsNoContent() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser("1");
    }

    @Test
    void testDeleteUser_nonExistingUserRequested_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        doThrow(new EntityNotFoundException("User")).when(userService).deleteUser(userId);

        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("User")));

        verify(userService, times(1)).deleteUser(userId);
    }

    @Test
    void testGetUserByEmail_existingEmailProvided_returnsUser() throws Exception {
        // Arrange
        String email = "john@example.com";
        when(userService.getUserByEmail(email)).thenReturn(testUser1);

        // Act & Assert
        mockMvc.perform(get("/api/users/by-email")
                .param("email", email)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).getUserByEmail(email);
    }

    @Test
    void testGetUserByEmail_nonExistingEmailProvided_returnsNotFound() throws Exception {
        // Arrange
        String email = "missing@example.com";
        when(userService.getUserByEmail(email)).thenThrow(new EntityNotFoundException("User"));

        // Act & Assert
        mockMvc.perform(get("/api/users/by-email")
                .param("email", email)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("User")));

        verify(userService, times(1)).getUserByEmail(email);
    }

    @Test
    void testLogin_validCredentials_returnsToken() throws Exception {
        // Arrange
        when(userService.login(any(LoginRequest.class))).thenReturn("jwt-token-123");

        // Act & Assert
        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt-token-123")));

        verify(userService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    void testLogin_invalidCredentials_returnsBadRequest() throws Exception {
        // Arrange
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid credentials")));

        verify(userService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    void testLogin_invalidRequestBody_returnsBadRequest() throws Exception {
        // Arrange
        LoginRequest invalidRequest = new LoginRequest("not-an-email", "");

        // Act & Assert
        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).login(any(LoginRequest.class));
    }
}
