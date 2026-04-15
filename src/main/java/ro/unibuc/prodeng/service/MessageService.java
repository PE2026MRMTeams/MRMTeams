package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.ForbiddenAccessException;
import ro.unibuc.prodeng.exception.InvalidLimitException;
import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.MessageRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.projection.MessagePreviewProjection;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.request.EditMessageRequest;
import ro.unibuc.prodeng.response.MessageResponse;

@Service
public class MessageService {

    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 100;
    private static final int MESSAGE_PREVIEW_LIMIT = 500;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuthContextService authContextService;


    /// Auxiliary methods for business logic:

    // RBAC: Shared guard for team-level read/create access
    private void isEnrolledOrAdmin(TeamEntity team, UserEntity user) {

        boolean isAdmin = user.role() != null && user.role().equalsIgnoreCase("admin");
        boolean isOwner = team.createdBy() != null && team.createdBy().equals(user.id());
        boolean isMember = team.members() != null && team.members().contains(user.id());

        if (!isAdmin && !isOwner && !isMember) {
            throw new ForbiddenAccessException("You are not allowed to access this team's messages!");
        }
    }

    // RBAC: admin controls members/content -> Shared guard for admin-only moderation actions
    private void isAdmin(UserEntity user) {
        if (user.role() == null || !user.role().equalsIgnoreCase("admin")) {
            throw new ForbiddenAccessException("Only admins can perform this operation");
        }
    }

    // Returns a team entity if the given teamId exists or throws exception if it doesn't
    private TeamEntity getTeamOrThrow(String teamId) {

        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        return teamRepository.findById(requiredTeamId)
                             .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));
    }

    // Converts a full MessageEntity to an API response
    private MessageResponse toResponse(MessageEntity message) {
        return new MessageResponse(
                message.id(),
                message.content(),
                message.teamId(),
                message.sentBy(),
                message.sentAt(),
                false
        );
    }

    // Maps a preview projection produced by the repository into the API response.
    private MessageResponse toListResponse(MessagePreviewProjection message) {
        return new MessageResponse(
                message.id(),
                message.content(),
                message.teamId(),
                message.sentBy(),
                message.sentAt(),
                message.truncated()
        );
    }

    private int sanitizeLimit(Integer requestedLimit) {

        if (requestedLimit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        if (requestedLimit < 1 || requestedLimit > MAX_PAGE_LIMIT) {
            throw new InvalidLimitException("Limit must be between 1 and " + MAX_PAGE_LIMIT);
        }
        return requestedLimit;
    }

    private List<MessagePreviewProjection> fetchPreviewPageFromCursor(String teamId, String cursor, int limit) {
        if (cursor == null || cursor.isBlank()) {
            return messageRepository.findPreviewPageByTeamIdOrderBySentAtDescIdDesc(teamId, limit, MESSAGE_PREVIEW_LIMIT);
        }

        return messageRepository.findByIdAndTeamId(cursor, teamId)
                .map(cursorMessage -> messageRepository.findPreviewPageByTeamIdFromCursor(
                        teamId,
                        cursorMessage.sentAt(),
                        cursorMessage.id(),
                        limit,
                        MESSAGE_PREVIEW_LIMIT
                ))
                .orElseGet(() -> {
                    try {
                        Instant cursorSentAt = Instant.parse(cursor);
                        return messageRepository.findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(teamId, cursorSentAt, limit, MESSAGE_PREVIEW_LIMIT);
                    } catch (DateTimeParseException ex) {
                        throw new InvalidLimitException("Cursor must be a valid message id for this team or a timestamp");
                    }
                });
    }


    /// Methods called by API endpoints from MessageController:

    public MessageResponse createMessage(String teamId, CreateMessageRequest requestBody) {

        // RBAC: Only team members or admins can publish content in a group.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        isEnrolledOrAdmin(team, currentUser);

        MessageEntity savedMessage = messageRepository.save(new MessageEntity(
                null,
                requestBody.content(),
                team.id(),
                currentUser.id(),
                Instant.now()
                )
        );

        return toResponse(savedMessage);
    }

    public List<MessageResponse> getMessagesByTeam(String teamId, String cursor, Integer limit) {

        // Restrict message visibility to enrolled users and admins.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        isEnrolledOrAdmin(team, currentUser);

        int pageLimit = sanitizeLimit(limit);
        return fetchPreviewPageFromCursor(team.id(), cursor, pageLimit).stream()
                .map(this::toListResponse)
                .toList();
    }

    public MessageResponse getMessageById(String teamId, String messageId) {

        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        isEnrolledOrAdmin(team, currentUser);

        MessageEntity message = messageRepository.findByIdAndTeamId(messageId, team.id())
                .orElseThrow(() -> new EntityNotFoundException("Message with id: " + messageId + " and team: " + team.id()));

        return toResponse(message);
    }

    public MessageResponse editMessage(String teamId, String messageId, EditMessageRequest requestBody) {

        // RBAC: Only the user who created the message can edit it
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        TeamEntity team = getTeamOrThrow(teamId);
        isEnrolledOrAdmin(team, currentUser);

        MessageEntity message = messageRepository.findByIdAndTeamId(messageId, team.id())
                .orElseThrow(() -> new EntityNotFoundException("Message with id: " + messageId + " and team: " + team.id()));

        // Check if the current user is the owner of the message
        if (!message.sentBy().equals(currentUser.id())) {
            throw new ForbiddenAccessException("You can only edit messages you created");
        }

        // Create a new MessageEntity with updated content but keep original metadata
        MessageEntity updatedMessage = messageRepository.save(new MessageEntity(
                message.id(),
                requestBody.content(),
                message.teamId(),
                message.sentBy(),
                message.sentAt()
        ));

        return toResponse(updatedMessage);
    }

    public void deleteMessage(String teamId, String messageId) {

        // RBAC: Only admins can moderate and remove team content.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        isAdmin(currentUser);

        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        String requiredMessageId = Objects.requireNonNull(messageId, "Message id is required");

        teamRepository.findById(requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

        MessageEntity message = messageRepository.findByIdAndTeamId(requiredMessageId, requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Message with id: " + requiredMessageId + " and team: " + requiredTeamId));

        messageRepository.deleteById(Objects.requireNonNull(message.id(), "Message id is required"));
    }
}