package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.exception.ForbiddenAccessException;
import ro.unibuc.prodeng.exception.InvalidLimitException;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.projection.MessagePreviewProjection;
import ro.unibuc.prodeng.response.MessageResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.request.EditMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private Instant now;
    private String userId;
    private String teamId;
    private String adminId;
    private String ownerId;
    private UserEntity enrolledUser;
    private UserEntity adminUser;
    private UserEntity nonEnrolledUser;
    private TeamEntity teamWithUser;
    private TeamEntity teamWithOwner;

    @BeforeEach
    void setUp() {

        now = Instant.now();
        userId = "user1";
        teamId = "team1";
        adminId = "admin1";
        ownerId = "user2";

        enrolledUser = createTestUser(userId, "Alice", "alice@test.com", "user", now);
        adminUser = createTestUser(adminId, "Admin", "admin@test.com", "admin", now);
        nonEnrolledUser = createTestUser("user-nonmember", "Bob", "bob@test.com", "user", now);
        
        teamWithUser = createTestTeam(teamId, "Test Team", userId, List.of(userId), now);
        teamWithOwner = createTestTeam(teamId, "Test Team", ownerId, List.of(ownerId), now);
    }

    // ========== Helper Methods ==========

    private UserEntity createTestUser(String id, String name, String email, String role, Instant createdAt) {
        return new UserEntity(id, name, email, "encodedPassword", role, createdAt);
    }

    private TeamEntity createTestTeam(String id, String name, String createdBy, List<String> members, Instant modifiedAt) {
        return new TeamEntity(id, name, "This is a test team", createdBy, members, modifiedAt);
    }

    private MessageEntity createTestMessage(String id, String content, String teamId, String sentBy, Instant sentAt) {
        return new MessageEntity(id, content, teamId, sentBy, sentAt);
    }

    private MessagePreviewProjection createTestPreview(String id, String content, String teamId, String sentBy, Instant sentAt, boolean truncated) {
        return new MessagePreviewProjection(id, content, teamId, sentBy, sentAt, truncated);
    }

    /// Tests for getMessageById

    @Test
    void testGetMessageById_existingMessageWithEnrolledUser_returnsMessage() throws EntityNotFoundException {

        // Arrange
        String messageId = "msg1";
        MessageEntity message = createTestMessage(messageId, "Hello World", teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
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
        String messageId = "msg1";
        MessageEntity message = createTestMessage(messageId, "Hello World", teamId, ownerId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithOwner));
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
        String messageId = "nonexistent";

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.getMessageById(teamId, messageId));
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
    }
    
    @Test
    void testGetMessageById_teamDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String nonexistentTeamId = "nonexistent";
        String messageId = "msg1";

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(nonexistentTeamId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.getMessageById(nonexistentTeamId, messageId));
        verify(teamRepository, times(1)).findById(nonexistentTeamId);
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }
    
    @Test
    void testGetMessageById_userNotEnrolledAndNotAdmin_throwsForbiddenAccessException() {

        // Arrange
        String messageId = "msg1";

        when(authContextService.getCurrentUserFromToken()).thenReturn(nonEnrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        
        // Act & Assert
        ForbiddenAccessException exception = assertThrows(ForbiddenAccessException.class,
            () -> messageService.getMessageById(teamId, messageId));
        
        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    /// Tests for getMessagesByTeam

    @Test
    void testGetMessagesByTeam_enrolledUserNoLimit_returnsPreviewListWithDefaultLimit() {

        // Arrange
        MessagePreviewProjection msg1 = createTestPreview("m1", "Hello", teamId, userId, now, false);
        MessagePreviewProjection msg2 = createTestPreview("m2", "World", teamId, userId, now.minusSeconds(5), false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500)))
                .thenReturn(List.of(msg1, msg2));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        // Assert
        assertEquals(2, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals("Hello", result.get(0).content());
        assertEquals(teamId, result.get(0).teamId());
        assertEquals(userId, result.get(0).sentBy());
        assertEquals(now, result.get(0).sentAt());
        assertFalse(result.get(0).isTruncated());

        verify(teamRepository).findById(teamId);
        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500));
    }

    @Test
    void testGetMessagesByTeam_enrolledUserWithLimit_returnsPreviewListWithRequestedLimit() {

        // Arrange
        MessagePreviewProjection msg1 = createTestPreview("m1", "Hello", teamId, userId, now, false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(10), eq(500)))
                .thenReturn(List.of(msg1));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals("Hello", result.get(0).content());
        assertFalse(result.get(0).isTruncated());

        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(10), eq(500));
    }

    @Test
    void testGetMessagesByTeam_adminUserNoLimit_canAccessTeamMessages() {

        // Arrange
        MessagePreviewProjection message = createTestPreview("m1", "Admin can read", teamId, ownerId, now, false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithOwner));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500)))
                .thenReturn(List.of(message));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());
        assertEquals(teamId, result.get(0).teamId());
        assertEquals(ownerId, result.get(0).sentBy());

        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500));
    }

    @Test
    void testGetMessagesByTeam_adminUserWithLimit_canAccessTeamMessagesWithRequestedLimit() {

        // Arrange
        MessagePreviewProjection message = createTestPreview("m1", "Admin can read", teamId, ownerId, now, false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithOwner));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(5), eq(500)))
                .thenReturn(List.of(message));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 5);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());

        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(5), eq(500));
    }

    @Test
    void testGetMessagesByTeam_teamDoesNotExist_throwsEntityNotFoundException() {
        // Arrange
        String nonexistentTeamId = "missing-team";

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(nonexistentTeamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.getMessagesByTeam(nonexistentTeamId, null, null));

        verify(teamRepository).findById(nonexistentTeamId);
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_teamHasNoMessages_returnsEmptyList() {

        // Arrange
        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500)))
                .thenReturn(List.of());

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(50), eq(500));
    }

    @Test
    void testGetMessagesByTeam_userNotEnrolledAndNotAdmin_throwsForbiddenAccessException() {

        // Arrange
        when(authContextService.getCurrentUserFromToken()).thenReturn(nonEnrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.getMessagesByTeam(teamId, null, null));

        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).findPreviewPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), anyInt(), anyInt());
    }

    @Test
    void testGetMessagesByTeam_limitBelowMinimum_throwsInvalidLimitException() {

        // Arrange
        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));

        // Act & Assert
        InvalidLimitException exception = assertThrows(
                InvalidLimitException.class,
                () -> messageService.getMessagesByTeam(teamId, null, 0));

        assertTrue(exception.getMessage().contains("Limit must be between 1 and 100"));
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_limitAboveMaximum_throwsInvalidLimitException() {

        // Arrange
        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));

        // Act & Assert
        InvalidLimitException exception = assertThrows(
                InvalidLimitException.class,
                () -> messageService.getMessagesByTeam(teamId, null, 101));

        assertTrue(exception.getMessage().contains("Limit must be between 1 and 100"));
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    @Test
    void testGetMessagesByTeam_invalidCursor_throwsInvalidLimitException() {

        // Arrange
        String invalidCursor = "invalid-cursor";

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(invalidCursor, teamId)).thenReturn(Optional.empty());

        // Act & Assert
        InvalidLimitException exception = assertThrows(
                InvalidLimitException.class,
                () -> messageService.getMessagesByTeam(teamId, invalidCursor, 10));

        assertTrue(exception.getMessage().contains("Cursor must be a valid message id for this team or a timestamp"));
        verify(messageRepository).findByIdAndTeamId(invalidCursor, teamId);
        verify(messageRepository, never()).findPreviewPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), anyInt(), anyInt());
    }

    @Test
    void testGetMessagesByTeam_cursorAsMessageId_usesMessageIdPaginationBranch() {

        // Arrange
        String cursorId = "cursor-message";
        Instant cursorSentAt = now.minusSeconds(30);
        MessageEntity cursorMessage = createTestMessage(cursorId, "Cursor", teamId, userId, cursorSentAt);
        MessagePreviewProjection olderMessage = createTestPreview("m-older", "Older", teamId, userId, cursorSentAt.minusSeconds(10), false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(cursorId, teamId)).thenReturn(Optional.of(cursorMessage));
        when(messageRepository.findPreviewPageByTeamIdFromCursor(eq(teamId), eq(cursorSentAt), eq(cursorId), eq(2), eq(500)))
                .thenReturn(List.of(olderMessage));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, cursorId, 2);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m-older", result.get(0).id());
        assertEquals("Older", result.get(0).content());
        verify(messageRepository).findPreviewPageByTeamIdFromCursor(eq(teamId), eq(cursorSentAt), eq(cursorId), eq(2), eq(500));
        verify(messageRepository, never()).findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(anyString(), any(Instant.class), anyInt(), anyInt());
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
    }

    @Test
    void testGetMessagesByTeam_cursorAsTimestamp_usesTimestampPaginationBranch() {

        // Arrange
        Instant cursorSentAt = now.minusSeconds(60);
        String cursor = cursorSentAt.toString();
        MessagePreviewProjection olderMessage = createTestPreview("m-older", "Older by timestamp", teamId, userId, cursorSentAt.minusSeconds(10), false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(cursor, teamId)).thenReturn(Optional.empty());
        when(messageRepository.findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(eq(teamId), eq(cursorSentAt), eq(3), eq(500)))
                .thenReturn(List.of(olderMessage));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, cursor, 3);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m-older", result.get(0).id());
        assertEquals("Older by timestamp", result.get(0).content());
        verify(messageRepository).findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(eq(teamId), eq(cursorSentAt), eq(3), eq(500));
        verify(messageRepository, never()).findPreviewPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), anyInt(), anyInt());
        verify(messageRepository, never()).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(anyString(), anyInt(), anyInt());
    }

    @Test
    void testGetMessagesByTeam_blankCursor_usesDefaultPaginationBranch() {

        // Arrange
        MessagePreviewProjection message = createTestPreview("m-blank", "Blank cursor path", teamId, userId, now, false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(4), eq(500)))
                .thenReturn(List.of(message));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, "   ", 4);

        // Assert
        assertEquals(1, result.size());
        assertEquals("m-blank", result.get(0).id());
        verify(messageRepository).findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(4), eq(500));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).findPreviewPageByTeamIdFromCursor(anyString(), any(Instant.class), anyString(), anyInt(), anyInt());
    }

    @Test
    void testGetMessagesByTeam_withNullContent_mapsToEmptyPreview() {

        // Arrange
        MessagePreviewProjection nullContentMessage = createTestPreview("m-null", "", teamId, userId, now, false);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(5), eq(500)))
                .thenReturn(List.of(nullContentMessage));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 5);

        // Assert
        assertEquals(1, result.size());
        assertEquals("", result.get(0).content());
        assertFalse(result.get(0).isTruncated());
    }

    @Test
    void testGetMessagesByTeam_withLongContent_returnsTruncatedPreview() {

        // Arrange
        String longContent = "x".repeat(600);
        MessagePreviewProjection longMessage = createTestPreview("m-long", longContent.substring(0, 500), teamId, userId, now, true);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(eq(teamId), eq(6), eq(500)))
                .thenReturn(List.of(longMessage));

        // Act
        List<MessageResponse> result = messageService.getMessagesByTeam(teamId, null, 6);

        // Assert
        assertEquals(1, result.size());
        assertEquals(500, result.get(0).content().length());
        assertTrue(result.get(0).isTruncated());
    }

    @Test
    void testGetMessageById_withNullRoleAndNullTeamMetadata_throwsForbiddenAccessException() {

        // Arrange
        UserEntity noRoleUser = createTestUser("user-no-role", "NoRole", "nrole@test.com", null, now);
        TeamEntity teamWithNulls = createTestTeam(teamId, "Team Nulls", null, null, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(noRoleUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithNulls));

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.getMessageById(teamId, "msg-null-branches"));

        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
    }

    /// Tests for createMessage

    @Test
    void testCreateMessage_enrolledUserCreatesMessage_returnsSavedMessageResponse() {
        // Arrange
        String messageContent = "This is a test message";
        CreateMessageRequest request = new CreateMessageRequest(messageContent);
        String savedMessageId = "saved-msg-1";
        MessageEntity savedMessage = createTestMessage(savedMessageId, messageContent, teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(savedMessage);

        // Act
        MessageResponse response = messageService.createMessage(teamId, request);

        // Assert
        assertNotNull(response);
        assertEquals(savedMessageId, response.id());
        assertEquals(messageContent, response.content());
        assertEquals(teamId, response.teamId());
        assertEquals(userId, response.sentBy());
        assertEquals(now, response.sentAt());
        assertFalse(response.isTruncated());

        verify(teamRepository).findById(teamId);
        verify(messageRepository).save(argThat(msg ->
                msg.content().equals(messageContent) &&
                msg.teamId().equals(teamId) &&
                msg.sentBy().equals(userId) &&
                msg.id() == null  // ID should be null before saving
        ));
    }

    @Test
    void testCreateMessage_adminUserCreatesMessage_returnsSavedMessageResponse() {
        // Arrange - Admin creates message in team they're not member of
        String messageContent = "Admin message";
        CreateMessageRequest request = new CreateMessageRequest(messageContent);
        String savedMessageId = "saved-msg-admin";
        MessageEntity savedMessage = createTestMessage(savedMessageId, messageContent, teamId, adminId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithOwner));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(savedMessage);

        // Act
        MessageResponse response = messageService.createMessage(teamId, request);

        // Assert
        assertNotNull(response);
        assertEquals(savedMessageId, response.id());
        assertEquals(messageContent, response.content());
        assertEquals(teamId, response.teamId());
        assertEquals(adminId, response.sentBy());
        assertFalse(response.isTruncated());

        verify(teamRepository).findById(teamId);
        verify(messageRepository).save(argThat(msg ->
                msg.content().equals(messageContent) &&
                msg.teamId().equals(teamId) &&
                msg.sentBy().equals(adminId)
        ));
    }

    @Test
    void testCreateMessage_userNotEnrolledAndNotAdmin_throwsForbiddenAccessException() {
        // Arrange
        CreateMessageRequest request = new CreateMessageRequest("Unauthorized message");

        when(authContextService.getCurrentUserFromToken()).thenReturn(nonEnrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.createMessage(teamId, request));

        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void testCreateMessage_teamDoesNotExist_throwsEntityNotFoundException() {
        // Arrange
        String nonexistentTeamId = "missing-team";
        CreateMessageRequest request = new CreateMessageRequest("Message content");

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(nonexistentTeamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> messageService.createMessage(nonexistentTeamId, request));

        verify(teamRepository).findById(nonexistentTeamId);
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void testCreateMessage_withLongContent_returnsTruncatedPreview() {
        // Arrange - Create content longer than MESSAGE_PREVIEW_LIMIT (500 chars)
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            longContent.append("This is a test message that will be truncated. ");
        }
        String fullContent = longContent.toString();
        CreateMessageRequest request = new CreateMessageRequest(fullContent);
        String savedMessageId = "long-msg";
        MessageEntity savedMessage = createTestMessage(savedMessageId, fullContent, teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(savedMessage);

        // Act
        MessageResponse response = messageService.createMessage(teamId, request);

        // Assert
        assertNotNull(response);
        assertEquals(savedMessageId, response.id());
        assertEquals(fullContent, response.content());  // Full content stored in entity
        assertEquals(teamId, response.teamId());
        assertEquals(userId, response.sentBy());
        assertFalse(response.isTruncated());  // toResponse() doesn't truncate, toListResponse() does

        verify(messageRepository).save(argThat(msg -> msg.content().equals(fullContent)));
    }

    @Test
    void testCreateMessage_teamOwnerCreatesMessage_successfullyCreates() {
        // Arrange - Team owner (who created the team) creates a message
        String messageContent = "Owner's message";
        CreateMessageRequest request = new CreateMessageRequest(messageContent);
        String savedMessageId = "owner-msg";
        UserEntity teamOwner = createTestUser(ownerId, "Owner", "owner@test.com", "user", now);
        MessageEntity savedMessage = createTestMessage(savedMessageId, messageContent, teamId, ownerId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(teamOwner);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithOwner));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(savedMessage);

        // Act
        MessageResponse response = messageService.createMessage(teamId, request);

        // Assert
        assertNotNull(response);
        assertEquals(savedMessageId, response.id());
        assertEquals(messageContent, response.content());
        assertEquals(ownerId, response.sentBy());

        verify(messageRepository).save(argThat(msg ->
                msg.content().equals(messageContent) &&
                msg.sentBy().equals(ownerId)
        ));
    }

    @Test
    void testCreateMessage_multipleUsersInTeam_eachCanCreateMessage() {
        // Arrange - Multiple team members
        String user2Id = "user3";
        UserEntity user2 = createTestUser(user2Id, "Charlie", "charlie@test.com", "user", now);
        TeamEntity teamWithMultipleMembers = createTestTeam(teamId, "Test Team", userId, List.of(userId, user2Id), now);
        
        CreateMessageRequest request = new CreateMessageRequest("User 2 message");
        String savedMessageId = "user2-msg";
        MessageEntity savedMessage = createTestMessage(savedMessageId, "User 2 message", teamId, user2Id, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(user2);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithMultipleMembers));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(savedMessage);

        // Act
        MessageResponse response = messageService.createMessage(teamId, request);

        // Assert
        assertNotNull(response);
        assertEquals(user2Id, response.sentBy());
        assertEquals("User 2 message", response.content());

        verify(messageRepository).save(argThat(msg -> msg.sentBy().equals(user2Id)));
    }

    /// Tests for editMessage

    @Test
    void testEditMessage_messageOwner_updatesContentAndPreservesMetadata() {

        // Arrange
        String messageId = "msg-edit";
        Instant sentAt = now.minusSeconds(30);
        MessageEntity existingMessage = createTestMessage(messageId, "Old content", teamId, userId, sentAt);
        EditMessageRequest request = new EditMessageRequest("Updated content");
        MessageEntity updatedMessage = createTestMessage(messageId, "Updated content", teamId, userId, sentAt);

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(existingMessage));
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(updatedMessage);

        // Act
        MessageResponse response = messageService.editMessage(teamId, messageId, request);

        // Assert
        assertNotNull(response);
        assertEquals(messageId, response.id());
        assertEquals("Updated content", response.content());
        assertEquals(teamId, response.teamId());
        assertEquals(userId, response.sentBy());
        assertEquals(sentAt, response.sentAt());
        assertFalse(response.isTruncated());

        verify(messageRepository).save(argThat(msg ->
                msg.id().equals(messageId) &&
                msg.content().equals("Updated content") &&
                msg.teamId().equals(teamId) &&
                msg.sentBy().equals(userId) &&
                msg.sentAt().equals(sentAt)
        ));
    }

    @Test
    void testEditMessage_nonOwner_throwsForbiddenAccessException() {

        // Arrange
        String messageId = "msg-edit";
        MessageEntity existingMessage = createTestMessage(messageId, "Old content", teamId, ownerId, now);
        EditMessageRequest request = new EditMessageRequest("Updated content");

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(existingMessage));

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.editMessage(teamId, messageId, request));

        assertTrue(exception.getMessage().contains("You can only edit messages you created"));
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void testEditMessage_messageNotFound_throwsEntityNotFoundException() {

        // Arrange
        String messageId = "missing-message";
        EditMessageRequest request = new EditMessageRequest("Updated content");

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.editMessage(teamId, messageId, request));
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void testEditMessage_userNotEnrolled_throwsForbiddenAccessException() {

        // Arrange
        String messageId = "msg-edit";
        EditMessageRequest request = new EditMessageRequest("Updated content");

        when(authContextService.getCurrentUserFromToken()).thenReturn(nonEnrolledUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.editMessage(teamId, messageId, request));

        assertTrue(exception.getMessage().contains("not allowed to access this team's messages"));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void testEditMessage_teamDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String missingTeamId = "missing-team";
        EditMessageRequest request = new EditMessageRequest("Updated content");

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);
        when(teamRepository.findById(missingTeamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.editMessage(missingTeamId, "msg-edit", request));
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    /// Tests for deleteMessage

    @Test
    void testDeleteMessage_adminWithExistingTeamAndMessage_deletesMessage() {

        // Arrange
        String messageId = "msg-delete";
        MessageEntity existingMessage = createTestMessage(messageId, "to delete", teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(existingMessage));

        // Act
        messageService.deleteMessage(teamId, messageId);

        // Assert
        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
        verify(messageRepository, times(1)).deleteById(messageId);
    }

    @Test
    void testDeleteMessage_nonAdmin_throwsForbiddenAccessException() {

        // Arrange
        String messageId = "msg-delete";

        when(authContextService.getCurrentUserFromToken()).thenReturn(enrolledUser);

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.deleteMessage(teamId, messageId));

        assertTrue(exception.getMessage().contains("Only admins can perform this operation"));
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteMessage_userWithNullRole_throwsForbiddenAccessException() {

        // Arrange
        UserEntity nullRoleUser = createTestUser("u-null", "Null Role", "nullrole@test.com", null, now);
        when(authContextService.getCurrentUserFromToken()).thenReturn(nullRoleUser);

        // Act & Assert
        ForbiddenAccessException exception = assertThrows(
                ForbiddenAccessException.class,
                () -> messageService.deleteMessage(teamId, "msg-delete"));

        assertTrue(exception.getMessage().contains("Only admins can perform this operation"));
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteMessage_teamDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String missingTeamId = "missing-team";
        String messageId = "msg-delete";

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(missingTeamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.deleteMessage(missingTeamId, messageId));

        verify(teamRepository, times(1)).findById(missingTeamId);
        verify(messageRepository, never()).findByIdAndTeamId(anyString(), anyString());
        verify(messageRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteMessage_messageDoesNotExist_throwsEntityNotFoundException() {

        // Arrange
        String messageId = "missing-message";

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> messageService.deleteMessage(teamId, messageId));

        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
        verify(messageRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteMessage_nullTeamId_throwsNullPointerException() {

        // Arrange
        String messageId = "msg-delete";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);

        // Act & Assert
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> messageService.deleteMessage(null, messageId));

        assertEquals("Team id is required", exception.getMessage());
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteMessage_nullMessageId_throwsNullPointerException() {

        // Arrange
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);

        // Act & Assert
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> messageService.deleteMessage(teamId, null));

        assertEquals("Message id is required", exception.getMessage());
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteMessage_messageEntityHasNullId_throwsNullPointerException() {

        // Arrange
        String messageId = "msg-delete";
        MessageEntity malformedMessage = createTestMessage(null, "to delete", teamId, userId, now);

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithUser));
        when(messageRepository.findByIdAndTeamId(messageId, teamId)).thenReturn(Optional.of(malformedMessage));

        // Act & Assert
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> messageService.deleteMessage(teamId, messageId));

        assertEquals("Message id is required", exception.getMessage());
        verify(teamRepository, times(1)).findById(teamId);
        verify(messageRepository, times(1)).findByIdAndTeamId(messageId, teamId);
        verify(messageRepository, never()).deleteById(anyString());
    }
}
