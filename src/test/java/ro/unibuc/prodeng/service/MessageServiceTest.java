package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.response.MessageResponse;

@ExtendWith(SpringExtension.class)
@SuppressWarnings("null")
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AuthContextService authContextService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void testCreateMessage_teamMemberProvided_createsMessage() {
        //Enrollment&access: admin controls members/content -> Allow team members to post content in their own groups.
        UserEntity member = new UserEntity("user-1", "User", "user@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", List.of("user-1"), Instant.now());
        MessageEntity saved = new MessageEntity("msg-1", "Hello", "team-1", "user-1", Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(member);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(messageRepository.save(org.mockito.ArgumentMatchers.<MessageEntity>notNull())).thenReturn(saved);

        MessageResponse result = messageService.createMessage("team-1", new CreateMessageRequest("Hello"));

        assertEquals("msg-1", result.id());
        assertEquals("Hello", result.content());
    }

    @Test
    void testGetMessagesByTeam_outsiderProvided_throwsForbidden() {
        //Enrollment&access: admin controls members/content -> Deny content visibility for users outside the target group.
        UserEntity outsider = new UserEntity("user-2", "User", "other@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(outsider);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesByTeam("team-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testDeleteMessage_nonAdminProvided_throwsForbidden() {
        //Enrollment&access: admin controls members/content -> Restrict deletion moderation to admin users.
        UserEntity user = new UserEntity("user-1", "User", "user@uni.ro", "pass", "user", Instant.now());
        when(authContextService.getCurrentUserFromToken()).thenReturn(user);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.deleteMessage("team-1", "msg-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testDeleteMessage_adminProvided_deletesMessage() {
        //Enrollment&access: admin controls members/content -> Allow admins to remove inappropriate team content.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", List.of("user-1"), Instant.now());
        MessageEntity message = new MessageEntity("msg-1", "Hello", "team-1", "user-1", Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(messageRepository.findByIdAndTeamId("msg-1", "team-1")).thenReturn(Optional.of(message));

        messageService.deleteMessage("team-1", "msg-1");

        verify(messageRepository).deleteById("msg-1");
    }
}