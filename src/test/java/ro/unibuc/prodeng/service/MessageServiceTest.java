package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.response.MessageResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private TeamRepository teamRepository;
    
    @Mock
    private AuthContextService authContextService;
    
    @InjectMocks
    private MessageService messageService;

    ///  Helper methods

    private UserEntity createTestUser(String id, String name, String email, String role, Instant createdAt) {
        return new UserEntity(id, name, email, "encodedPassword", role, createdAt);
    }

    private TeamEntity createTestTeam(String id, String name, String createdBy, List<String> members, Instant modifiedAt) {
        return new TeamEntity(id, name, "This is a test team", createdBy, members, modifiedAt);
    }

    private MessageEntity createTestMessage(String id, String content, String teamId, String sentBy, Instant sentAt) {
        return new MessageEntity(id, content, teamId, sentBy, sentAt);
    }

    /// Tests for auxiliary methods inside the service

    /// Tests for methods called by MessageController
    
    @Test
    void testGetMessageById_existingMessageWithEnrolledUser_returnsMessage() throws EntityNotFoundException {

        // Arrange
        String userId = "user1";
        String messageId = "msg1";
        String teamId = "team1";
        Instant now = Instant.now();
        
        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        MessageEntity message = createTestMessage(messageId, "Hello World", teamId, userId, now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(message));
        
        // Act
        MessageResponse response = messageService.getMessageById(teamId, messageId);
        
        // Assert
        assertNotNull(response);
        assertEquals(messageId, response.id());
        assertEquals("Hello World", response.content());
        assertEquals(teamId, response.teamId());
        assertEquals(userId, response.sentBy());
        assertEquals(now, response.sentAt());
        assertFalse(response.isTruncated());
        
        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
    }
    
    @Test
    void testGetMessageById_existingMessageWithAdmin_returnsMessage() throws EntityNotFoundException {

        // Arrange - Admin user accessing a team they're not member of
        String teamId = "team1";
        String messageId = "msg1";
        String adminId = "admin1";
        String ownerId = "user1";
        Instant now = Instant.now();
        
        UserEntity admin = createTestUser(adminId, "Admin", "admin@test.com", "admin", now);
        MessageEntity message = createTestMessage(messageId, "Hello World", teamId, ownerId, now);
        TeamEntity team = createTestTeam(teamId, "Test Team", ownerId, List.of(ownerId), now);
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(message));
        
        // Act
        MessageResponse response = messageService.getMessageById(teamId, messageId);
        
        // Assert
        assertNotNull(response);
        assertEquals(messageId, response.id());
        assertEquals("Hello World", response.content());
        assertEquals(teamId, response.teamId());
        assertEquals(ownerId, response.sentBy());
        assertEquals(now, response.sentAt());
        assertFalse(response.isTruncated());

        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
    }
    
    @Test
    void testGetMessageById_messageDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String teamId = "team1";
        String messageId = "nonexistent";
        String userId = "user1";
        Instant now = Instant.now();
        
        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.getMessageById(teamId, messageId));
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
    }
    
    @Test
    void testGetMessageById_teamDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String teamId = "nonexistent";
        String messageId = "msg1";
        String userId = "user1";
        Instant now = Instant.now();
        
        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.getMessageById(teamId, messageId));
        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }
    
    @Test
    void testGetMessageById_userNotEnrolledAndNotAdmin_throwsForbiddenException() {

        // Arrange
        String teamId = "team1";
        String messageId = "msg1";
        String userId = "user1";
        String teamOwnerId = "user2";
        Instant now = Instant.now();
        
        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", teamOwnerId, List.of(teamOwnerId), now);
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, 
            () -> messageService.getMessageById(teamId, messageId));
        
        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

        
    @Test
    void testGetMessagesByTeam_enrolledUserNoLimit_returnsPreviewListWithDefaultLimit() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        MessageEntity msg1 = createTestMessage("m1", "Hello", teamId, userId, now);
        MessageEntity msg2 = createTestMessage("m2", "World", teamId, userId, now.minusSeconds(5));

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByTeamIdOrderBySentAtDescIdDesc(eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(msg1, msg2));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        assertEquals(2, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals("Hello", result.get(0).content());
        assertEquals(teamId, result.get(0).teamId());
        assertEquals(userId, result.get(0).sentBy());
        assertEquals(now, result.get(0).sentAt());
        assertFalse(result.get(0).isTruncated());

        verify(teamRepository).findById(teamId);
        verify(messageRepository).findByTeamIdOrderBySentAtDescIdDesc(
                eq(teamId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 50));
    }

    @Test
    void testGetMessagesByTeam_enrolledUserWithLimit_returnsPreviewListWithRequestedLimit() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        MessageEntity msg1 = createTestMessage("m1", "Hello", teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByTeamIdOrderBySentAtDescIdDesc(eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(msg1));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 10);

        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals("Hello", result.get(0).content());
        assertFalse(result.get(0).isTruncated());

        verify(messageRepository).findByTeamIdOrderBySentAtDescIdDesc(
                eq(teamId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
    }

    @Test
    void testGetMessagesByTeam_adminUserNoLimit_canAccessTeamMessages() {

        String adminId = "admin1";
        String ownerId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity admin = createTestUser(adminId, "Admin", "admin@test.com", "admin", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", ownerId, List.of(ownerId), now);
        MessageEntity message = createTestMessage("m1", "Admin can read", teamId, ownerId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByTeamIdOrderBySentAtDescIdDesc(eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(message));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals(teamId, result.get(0).teamId());
        assertEquals(ownerId, result.get(0).sentBy());

        verify(messageRepository).findByTeamIdOrderBySentAtDescIdDesc(
                eq(teamId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 50));
    }

    @Test
    void testGetMessagesByTeam_adminUserWithLimit_canAccessTeamMessagesWithRequestedLimit() {

        String adminId = "admin1";
        String ownerId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity admin = createTestUser(adminId, "Admin", "admin@test.com", "admin", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", ownerId, List.of(ownerId), now);
        MessageEntity message = createTestMessage("m1", "Admin can read", teamId, ownerId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByTeamIdOrderBySentAtDescIdDesc(eq(teamId), any(Pageable.class)))
                .thenReturn(List.of(message));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 5);

        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());

        verify(messageRepository).findByTeamIdOrderBySentAtDescIdDesc(
                eq(teamId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 5));
    }

    @Test
    void testGetMessagesByTeam_teamDoesNotExist_throwsEntityNotFoundException() {

        String teamId = "missing-team";
        Instant now = Instant.now();
        UserEntity currentUser = createTestUser("user1", "Alice", "alice@test.com", "user", now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> messageService.getMessagesByTeam(teamId, null, null));

        verify(teamRepository).findById(teamId);
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_teamHasNoMessages_returnsEmptyList() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByTeamIdOrderBySentAtDescIdDesc(eq(teamId), any(Pageable.class)))
                .thenReturn(List.of());

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageRepository).findByTeamIdOrderBySentAtDescIdDesc(
                eq(teamId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 50));
    }

    @Test
    void testGetMessagesByTeam_userNotEnrolledAndNotAdmin_throwsForbiddenException() {

        String userId = "user1";
        String teamId = "team1";
        String ownerId = "owner1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", ownerId, List.of(ownerId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> messageService.getMessagesByTeam(teamId, null, null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).findPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), any(Pageable.class));
    }

    @Test
    void testGetMessagesByTeam_limitBelowMinimum_throwsBadRequest() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> messageService.getMessagesByTeam(teamId, null, 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Limit must be between 1 and 100"));
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_limitAboveMaximum_throwsBadRequest() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> messageService.getMessagesByTeam(teamId, null, 101));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Limit must be between 1 and 100"));
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_invalidCursor_throwsBadRequest() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();
        String invalidCursor = "invalid-cursor";

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(invalidCursor, teamId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> messageService.getMessagesByTeam(teamId, invalidCursor, 10));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Cursor must be a valid message id for this team or a timestamp"));
        verify(messageRepository).findByIdAndTeamId(invalidCursor, teamId);
        verify(messageRepository, never()).findPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), any(Pageable.class));
    }

    @Test
    void testGetMessagesByTeam_cursorAsMessageId_usesMessageIdPaginationBranch() {

        String userId = "user1";
        String teamId = "team1";
        String cursorId = "cursor-message";
        Instant now = Instant.now();
        Instant cursorSentAt = now.minusSeconds(30);

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);
        MessageEntity cursorMessage = createTestMessage(cursorId, "Cursor", teamId, userId, cursorSentAt);
        MessageEntity olderMessage = createTestMessage("m-older", "Older", teamId, userId, cursorSentAt.minusSeconds(10));

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(cursorId, teamId)).thenReturn(Optional.of(cursorMessage));
        when(messageRepository.findPageByTeamIdFromCursor(eq(teamId), eq(cursorSentAt), eq(cursorId), any(Pageable.class)))
                .thenReturn(List.of(olderMessage));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, cursorId, 2);

        assertEquals(1, result.size());
        assertEquals("m-older", result.get(0).id());
        assertEquals("Older", result.get(0).content());
        verify(messageRepository).findPageByTeamIdFromCursor(
                eq(teamId),
                eq(cursorSentAt),
                eq(cursorId),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 2));
        verify(messageRepository, never()).findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), any(Pageable.class));
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
    }

    @Test
    void testGetMessagesByTeam_cursorAsTimestamp_usesTimestampPaginationBranch() {

        String userId = "user1";
        String teamId = "team1";
        Instant now = Instant.now();
        Instant cursorSentAt = now.minusSeconds(60);
        String cursor = cursorSentAt.toString();

        UserEntity currentUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        TeamEntity team = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);
        MessageEntity olderMessage = createTestMessage("m-older", "Older by timestamp", teamId, userId, cursorSentAt.minusSeconds(10));

        when(authContextService.getCurrentUserFromToken()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId(cursor, teamId)).thenReturn(Optional.empty());
        when(messageRepository.findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(eq(teamId), eq(cursorSentAt), any(Pageable.class)))
                .thenReturn(List.of(olderMessage));

        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, cursor, 3);

        assertEquals(1, result.size());
        assertEquals("m-older", result.get(0).id());
        assertEquals("Older by timestamp", result.get(0).content());
        verify(messageRepository).findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(
                eq(teamId),
                eq(cursorSentAt),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 3));
        verify(messageRepository, never()).findPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), any(Pageable.class));
        verify(messageRepository, never()).findByTeamIdOrderBySentAtDescIdDesc(anyString(), any(Pageable.class));
    }
}
