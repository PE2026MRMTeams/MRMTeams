package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.response.MessageResponse;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuthContextService authContextService;

    public MessageResponse createMessage(String teamId, CreateMessageRequest requestBody) {
        //Enrollment&access: admin controls members/content -> Only team members or admins can publish content in a group.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        ensureTeamReadableByUser(team, currentUser);

        MessageEntity saved = messageRepository.save(new MessageEntity(
                null,
                requestBody.content(),
                team.id(),
                currentUser.id(),
                Instant.now()));
        return toResponse(saved);
    }

    public List<MessageResponse> getMessagesByTeam(String teamId) {
        //Enrollment&access: admin controls members/content -> Restrict message visibility to enrolled users and admins.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        ensureTeamReadableByUser(team, currentUser);

        return messageRepository.findByTeamId(team.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteMessage(String teamId, String messageId) {
        //Enrollment&access: admin controls members/content -> Only admins can moderate and remove team content.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        ensureAdmin(currentUser);

        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        String requiredMessageId = Objects.requireNonNull(messageId, "Message id is required");

        teamRepository.findById(requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

        MessageEntity message = messageRepository.findByIdAndTeamId(requiredMessageId, requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Message with id: " + requiredMessageId + " and team: " + requiredTeamId));

        messageRepository.deleteById(Objects.requireNonNull(message.id(), "Message id is required"));
    }

    //Enrollment&access: admin controls members/content -> Shared guard for team-level read/create access.
    private void ensureTeamReadableByUser(TeamEntity team, UserEntity user) {
        boolean isAdmin = user.role() != null && "admin".equalsIgnoreCase(user.role());
        boolean isOwner = team.createdBy() != null && team.createdBy().equals(user.id());
        boolean isMember = team.members() != null && team.members().contains(user.id());
        if (!isAdmin && !isOwner && !isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this team's messages");
        }
    }

    //Enrollment&access: admin controls members/content -> Shared guard for admin-only moderation actions.
    private void ensureAdmin(UserEntity user) {
        if (user.role() == null || !"admin".equalsIgnoreCase(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can perform this operation");
        }
    }

    //Enrollment&access: admin controls members/content -> Resolve and validate target team once per operation.
    private TeamEntity getTeamOrThrow(String teamId) {
        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        return teamRepository.findById(requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));
    }

    //Enrollment&access: admin controls members/content -> Convert message persistence model to API response model.
    private MessageResponse toResponse(MessageEntity message) {
        return new MessageResponse(
                message.id(),
                message.content(),
                message.teamId(),
                message.sentBy(),
                message.sentAt());
    }
}