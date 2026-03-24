package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import jakarta.servlet.http.HttpServletRequest;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;

@ExtendWith(SpringExtension.class)
class AuthContextServiceTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthContextService authContextService;

    private static final String SECRET = "cheia-lui-mihaita-de-la-332";
    private static final String VALID_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
    }

    private String generateValidToken(String email) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        return JWT.create()
                .withSubject(email)
                .withIssuedAt(new java.util.Date())
                .withExpiresAt(new java.util.Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);
    }

    // Enrollment&access: RBAC -> Test getEmailFromToken extracts email correctly
    @Test
    void testGetEmailFromToken_withValidToken_returnsEmailSubject() {
        // Arrange
        String token = generateValidToken(VALID_EMAIL);
        String authHeader = "Bearer " + token;
        when(request.getHeader("Authorization")).thenReturn(authHeader);

        // Act
        String result = authContextService.getEmailFromToken();

        // Assert
        assertEquals(VALID_EMAIL, result);
    }

    // Enrollment&access: RBAC -> Test missing Bearer token is rejected
    @Test
    void testGetEmailFromToken_withNoAuthHeader_throwsUnauthorizedException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                authContextService.getEmailFromToken()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Token doesn't exist"));
    }

    // Enrollment&access: RBAC -> Test malformed Bearer header is rejected
    @Test
    void testGetEmailFromToken_withMissingBearerPrefix_throwsUnauthorizedException() {
        // Arrange
        String token = generateValidToken(VALID_EMAIL);
        when(request.getHeader("Authorization")).thenReturn(token); // Missing "Bearer " prefix

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                authContextService.getEmailFromToken()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    // Enrollment&access: RBAC -> Test invalid token signature is rejected
    @Test
    void testGetEmailFromToken_withInvalidTokenSignature_throwsUnauthorizedException() {
        // Arrange
        String invalidToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";
        when(request.getHeader("Authorization")).thenReturn(invalidToken);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                authContextService.getEmailFromToken()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Invalide or expired token"));
    }

    // Enrollment&access: RBAC -> Test getCurrentUserFromToken retrieves user entity
    @Test
    void testGetCurrentUserFromToken_withValidToken_returnsUserEntity() {
        // Arrange
        String token = generateValidToken(VALID_EMAIL);
        String authHeader = "Bearer " + token;
        when(request.getHeader("Authorization")).thenReturn(authHeader);

        UserEntity testUser = new UserEntity("user-1", "Test User", VALID_EMAIL, "password", "admin", Instant.now());
        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(testUser));

        // Act
        UserEntity result = authContextService.getCurrentUserFromToken();

        // Assert
        assertNotNull(result);
        assertEquals("user-1", result.id());
        assertEquals("Test User", result.name());
        assertEquals(VALID_EMAIL, result.email());
        assertEquals("admin", result.role());
    }

    // Enrollment&access: RBAC -> Test getCurrentUserFromToken throws when user not found
    @Test
    void testGetCurrentUserFromToken_withValidTokenButUserNotFound_throwsEntityNotFoundException() {
        // Arrange
        String token = generateValidToken(VALID_EMAIL);
        String authHeader = "Bearer " + token;
        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                authContextService.getCurrentUserFromToken()
        );
        assertTrue(exception.getMessage().contains(VALID_EMAIL));
    }

    // Enrollment&access: RBAC -> Test getCurrentUserFromToken respects authentication
    @Test
    void testGetCurrentUserFromToken_withInvalidToken_throwsUnauthorizedException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token-format");

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                authContextService.getCurrentUserFromToken()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

}
