package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.TeamJoinRequestEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.TeamJoinRequestRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.AddTeamMemberRequest;
import ro.unibuc.prodeng.request.CreateTeamRequest;
import ro.unibuc.prodeng.response.TeamJoinRequestResponse;
import ro.unibuc.prodeng.response.TeamResponse;

@Service
public class TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamJoinRequestRepository teamJoinRequestRepository;

    @Autowired
    private AuthContextService authContextService;

    public TeamResponse createTeam(CreateTeamRequest requestBody) {
        // Enrollment&access: RBAC -> Only administrators are allowed to create new teams.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        ensureAdmin(currentUser);

        // Enrollment&access: RBAC -> Auto-enroll the creator in the team members list for consistent access checks.
        TeamEntity team = new TeamEntity(
                null,
                requestBody.name(),
                requestBody.description(),
                currentUser.id(),
                List.of(currentUser.id()),
                Instant.now());

        TeamEntity saved = teamRepository.save(team);
        return toResponse(saved);
    }

    public TeamResponse addMember(String teamId, AddTeamMemberRequest requestBody) {
        // Enrollment&access: RBAC -> Only administrators can manage membership of any team.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        ensureAdmin(currentUser);

        // Enrollment&access: RBAC -> Enforce non-null identifiers before repository calls guarded by @NonNull contracts.
        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        String requiredUserId = Objects.requireNonNull(requestBody.userId(), "User id is required");

        // Enrollment&access: RBAC -> Validate both team and target user existence before mutating enrollment.
        TeamEntity existingTeam = teamRepository.findById(requiredTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));
        UserEntity targetUser = userRepository.findById(requiredUserId)
            .orElseThrow(() -> new EntityNotFoundException("User with id: " + requiredUserId));

        // Enrollment&access: RBAC -> Keep enrollment idempotent by avoiding duplicate member entries.
        List<String> updatedMembers = existingTeam.members() == null
            ? new ArrayList<>()
            : new ArrayList<>(existingTeam.members());
        if (!updatedMembers.contains(targetUser.id())) {
            updatedMembers.add(targetUser.id());
        }

        TeamEntity updatedTeam = new TeamEntity(
                existingTeam.id(),
                existingTeam.name(),
                existingTeam.description(),
                existingTeam.createdBy(),
                updatedMembers,
                Instant.now());

        TeamEntity saved = teamRepository.save(updatedTeam);
        return toResponse(saved);
    }

    public TeamResponse getTeamById(String teamId) {
        // Enrollment&access: RBAC -> Allow access only for admins or users enrolled in the requested team.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
        TeamEntity team = teamRepository.findById(requiredTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

        boolean isAdmin = isAdmin(currentUser);
        boolean isOwner = team.createdBy() != null && team.createdBy().equals(currentUser.id());
        boolean isMember = team.members() != null && team.members().contains(currentUser.id());
        if (!isAdmin && !isOwner && !isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this team");
        }

        return toResponse(team);
    }

    public List<TeamResponse> getTeamsForCurrentUser() {
        // Enrollment&access: RBAC -> Admin users can view all teams, regular users only teams where they are enrolled.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        List<TeamEntity> teams = teamRepository.findAll();
        if (isAdmin(currentUser)) {
            return teams.stream().map(this::toResponse).toList();
        }

        return teams.stream()
                .filter(team -> (team.createdBy() != null && team.createdBy().equals(currentUser.id()))
                        || (team.members() != null && team.members().contains(currentUser.id())))
                .map(this::toResponse)
                .toList();
    }

            public TeamJoinRequestResponse requestToJoinTeam(String teamId) {
            //Enrollment&access: Manage group control -> Allow authenticated users to submit join requests instead of auto-joining teams.
            UserEntity currentUser = authContextService.getCurrentUserFromToken();
            String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");

            //Enrollment&access: Manage group control -> Validate the team before storing a pending join request.
            TeamEntity team = teamRepository.findById(requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

            //Enrollment&access: Manage group control -> Prevent redundant requests from already enrolled users.
            if (team.members() != null && team.members().contains(currentUser.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a team member");
            }

            //Enrollment&access: Manage group control -> Ensure one pending request per user/team pair.
            if (teamJoinRequestRepository.existsByTeamIdAndUserId(requiredTeamId, currentUser.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A join request already exists for this user and team");
            }

            //Enrollment&access: Manage group control -> Persist request for later admin approval.
            TeamJoinRequestEntity savedRequest = teamJoinRequestRepository.save(
                new TeamJoinRequestEntity(null, requiredTeamId, currentUser.id(), Instant.now()));
            return toJoinRequestResponse(savedRequest);
            }

            public List<TeamJoinRequestResponse> getJoinRequests(String teamId) {
            //Enrollment&access: Manage group control -> Restrict pending-requests visibility to administrators.
            UserEntity currentUser = authContextService.getCurrentUserFromToken();
            ensureAdmin(currentUser);
            String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");

            //Enrollment&access: Manage group control -> Return only requests for the selected team.
            return teamJoinRequestRepository.findByTeamId(requiredTeamId).stream()
                .map(this::toJoinRequestResponse)
                .toList();
            }

            public TeamResponse approveJoinRequest(String teamId, String userId) {
            //Enrollment&access: Manage group control -> Allow only administrators to approve membership requests.
            UserEntity currentUser = authContextService.getCurrentUserFromToken();
            ensureAdmin(currentUser);

            String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
            String requiredUserId = Objects.requireNonNull(userId, "User id is required");

            //Enrollment&access: Manage group control -> Validate the requested team, user and pending request before approval.
            TeamEntity existingTeam = teamRepository.findById(requiredTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));
            userRepository.findById(requiredUserId)
                .orElseThrow(() -> new EntityNotFoundException("User with id: " + requiredUserId));
            TeamJoinRequestEntity joinRequest = teamJoinRequestRepository.findByTeamIdAndUserId(requiredTeamId, requiredUserId)
                .orElseThrow(() -> new EntityNotFoundException("Join request for user: " + requiredUserId + " and team: " + requiredTeamId));

            //Enrollment&access: Manage group control -> Move approved users to members list and clear pending requests.
            List<String> updatedMembers = existingTeam.members() == null
                ? new ArrayList<>()
                : new ArrayList<>(existingTeam.members());
            if (!updatedMembers.contains(requiredUserId)) {
                updatedMembers.add(requiredUserId);
            }

            TeamEntity updatedTeam = new TeamEntity(
                existingTeam.id(),
                existingTeam.name(),
                existingTeam.description(),
                existingTeam.createdBy(),
                updatedMembers,
                Instant.now());

            TeamEntity savedTeam = teamRepository.save(updatedTeam);
            //Enrollment&access: Manage group control -> Remove the processed request after a successful approval.
            teamJoinRequestRepository.deleteById(Objects.requireNonNull(joinRequest.id(), "Join request id is required"));
            return toResponse(savedTeam);
            }

        public TeamResponse removeMember(String teamId, String userId) {
            //Enrollment&access: admin controls members/content -> Restrict member-removal operations to administrators.
            UserEntity currentUser = authContextService.getCurrentUserFromToken();
            ensureAdmin(currentUser);

            String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
            String requiredUserId = Objects.requireNonNull(userId, "User id is required");

            TeamEntity existingTeam = teamRepository.findById(requiredTeamId)
                    .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

            //Enrollment&access: admin controls members/content -> Protect team ownership from accidental removal.
            if (existingTeam.createdBy() != null && existingTeam.createdBy().equals(requiredUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team owner cannot be removed from members");
            }

            List<String> updatedMembers = existingTeam.members() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(existingTeam.members());
            boolean removed = updatedMembers.remove(requiredUserId);
            if (!removed) {
                throw new EntityNotFoundException("Member with user id: " + requiredUserId + " in team: " + requiredTeamId);
            }

            TeamEntity updatedTeam = new TeamEntity(
                    existingTeam.id(),
                    existingTeam.name(),
                    existingTeam.description(),
                    existingTeam.createdBy(),
                    updatedMembers,
                    Instant.now());

            return toResponse(teamRepository.save(updatedTeam));
        }

        public void rejectJoinRequest(String teamId, String userId) {
            //Enrollment&access: admin controls members/content -> Restrict rejection of pending requests to administrators.
            UserEntity currentUser = authContextService.getCurrentUserFromToken();
            ensureAdmin(currentUser);

            String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");
            String requiredUserId = Objects.requireNonNull(userId, "User id is required");

            TeamJoinRequestEntity joinRequest = teamJoinRequestRepository.findByTeamIdAndUserId(requiredTeamId, requiredUserId)
                    .orElseThrow(() -> new EntityNotFoundException("Join request for user: " + requiredUserId + " and team: " + requiredTeamId));

            //Enrollment&access: admin controls members/content -> Remove pending request as an explicit moderation decision.
            teamJoinRequestRepository.deleteById(Objects.requireNonNull(joinRequest.id(), "Join request id is required"));
        }

    // Enrollment&access: RBAC -> Shared guard for admin-only operations.
    private void ensureAdmin(UserEntity user) {
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can perform this operation");
        }
    }

    // Enrollment&access: RBAC -> Normalize role checks in one place to avoid duplicated string logic.
    private boolean isAdmin(UserEntity user) {
        return user.role() != null && "admin".equalsIgnoreCase(user.role());
    }

    // Enrollment&access: RBAC -> Expose safe team data without leaking unrelated internals.
    private TeamResponse toResponse(TeamEntity team) {
        return new TeamResponse(
                team.id(),
                team.name(),
                team.description(),
                team.createdBy(),
                team.members(),
                team.modifiedAt());
    }

    //Enrollment&access: Manage group control -> Convert pending-request entities into API-safe response objects.
    private TeamJoinRequestResponse toJoinRequestResponse(TeamJoinRequestEntity joinRequest) {
        return new TeamJoinRequestResponse(
                joinRequest.id(),
                joinRequest.teamId(),
                joinRequest.userId(),
                joinRequest.requestedAt());
    }
}