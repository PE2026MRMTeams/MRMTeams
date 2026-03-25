package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.LoginRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import com.auth0.jwt.JWT;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void testGetAllUsers_withMultipleUsers_returnsAllUsers() {
        // Arrange
        List<UserEntity> users = Arrays.asList(
                new UserEntity("1", "Alice", "alice@example.com", "password", "admin",Instant.now()),
                new UserEntity("2", "Bob", "bob@example.com", "password2", "admin",Instant.now())
        );
        when(userRepository.findAll()).thenReturn(users);

        // Act
        List<UserResponse> result = userService.getAllUsers();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).name());
        assertEquals("Bob", result.get(1).name());
    }

    @Test
    void testGetUserById_existingUserRequested_returnsUser() throws EntityNotFoundException {
        // Arrange
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", "password", "admin",Instant.now());
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getUserById("1");

        // Assert
        assertNotNull(result);
        assertEquals("Alice", result.name());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testGetUserById_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserById("999"));
    }

    @Test
    void testCreateUser_newUserWithValidData_createsAndReturnsUser() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Alice", "alice@example.com","password", "admin");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            // Simulate MongoDB generating an ID for new entities
            String id = "generated-id-123";
            return new UserEntity(id, entity.name(), entity.email(), entity.password(), entity.role(), entity.createdAt());
        });

        // Act
        UserResponse result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("Alice", result.name());
        assertEquals("alice@example.com", result.email());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void testCreateUser_duplicateEmail_throwsIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Alice", "alice@example.com", "password", "admin");
        UserEntity existing = new UserEntity("1", "Existing", "alice@example.com", "pass", "user", Instant.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(request));

        assertTrue(exception.getMessage().contains("Email already exists"));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void testCreateUser_nullRole_defaultsToUserAndEncryptsPassword() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Alice", "alice@example.com", "password123", null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponse result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("user", saved.role());
        assertNotEquals("password123", saved.password());
        assertTrue(new BCryptPasswordEncoder().matches("password123", saved.password()));
    }

    @Test
    void testChangeName_existingUserRequested_changesNameSuccessfully() throws EntityNotFoundException {
        // Arrange
        UserEntity existing = new UserEntity("1", "Alice", "alice@example.com", "password", "admin",Instant.now());
        when(userRepository.findById("1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            // Simulate MongoDB generating an ID for new entities
            String id = entity.id() == null ? "generated-id-123" : entity.id();
            return new UserEntity(id, entity.name(), entity.email(), entity.password(), entity.role(), entity.createdAt());
        });

        // Act
        UserResponse result = userService.changeName("1", "Alicia");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("Alicia", result.name());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testChangeName_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.changeName("999", "NewName"));
    }

    @Test
    void testDeleteUser_existingUserRequested_deletesSuccessfully() throws EntityNotFoundException {
        // Arrange
        when(userRepository.existsById("1")).thenReturn(true);

        // Act
        userService.deleteUser("1");

        // Assert
        verify(userRepository, times(1)).deleteById("1");
    }

    @Test
    void testDeleteUser_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.existsById("999")).thenReturn(false);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.deleteUser("999"));
    }

    @Test
    void testGetUserEntityById_existingUserRequested_returnsEntity() {
        // Arrange
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", "password", "admin", Instant.now());
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userService.getUserEntityById("1");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("Alice", result.name());
    }

    @Test
    void testGetUserEntityById_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("404")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserEntityById("404"));
    }

    @Test
    void testGetUserByEmail_existingEmail_returnsUserResponse() {
        // Arrange
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", "password", "admin", Instant.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getUserByEmail("alice@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testGetUserByEmail_nonExistingEmail_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserByEmail("missing@example.com"));
    }

    @Test
    void testGetUserEntityByEmail_existingEmail_returnsEntity() {
        // Arrange
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", "password", "admin", Instant.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userService.getUserEntityByEmail("alice@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testGetUserEntityByEmail_nonExistingEmail_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserEntityByEmail("missing@example.com"));
    }

    @Test
    void testLogin_validCredentials_returnsTokenWithSubjectEmail() {
        // Arrange
        String rawPassword = "password123";
        String encodedPassword = new BCryptPasswordEncoder().encode(rawPassword);
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", encodedPassword, "user", Instant.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        String token = userService.login(new LoginRequest("alice@example.com", rawPassword));

        // Assert
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("alice@example.com", JWT.decode(token).getSubject());
    }

    @Test
    void testLogin_invalidEmail_throwsIllegalArgumentException() {
        // Arrange
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(new LoginRequest("missing@example.com", "password123")));

        assertEquals("Invalid email", exception.getMessage());
    }

    @Test
    void testLogin_invalidPassword_throwsIllegalArgumentException() {
        // Arrange
        String encodedPassword = new BCryptPasswordEncoder().encode("correct-password");
        UserEntity user = new UserEntity("1", "Alice", "alice@example.com", encodedPassword, "user", Instant.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.login(new LoginRequest("alice@example.com", "wrong-password")));

        assertEquals("Invalid password", exception.getMessage());
    }
}
