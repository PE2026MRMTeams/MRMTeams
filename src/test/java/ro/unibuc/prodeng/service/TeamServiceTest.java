package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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
import ro.unibuc.prodeng.response.TeamResponse;

@ExtendWith(SpringExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamJoinRequestRepository teamJoinRequestRepository;

    @Mock
    private AuthContextService authContextService;

    @InjectMocks
    private TeamService teamService;

    private UserEntity adminUser;
    private UserEntity regularUser;
    private TeamEntity testTeam;

    @BeforeEach
    void setUp() {
        adminUser = new UserEntity("admin-1", "Admin User", "admin@example.com", "password", "admin", Instant.now());
        regularUser = new UserEntity("user-1", "Regular User", "user@example.com", "password", "user", Instant.now());
        testTeam = new TeamEntity(
            "team-1",
            "Test Team",
            "A test team",
            adminUser.id(),
            List.of(adminUser.id()),
            Instant.now()
        );
    }

    // Enrollment&access: RBAC -> Only administrators are allowed to create new teams
    @Test
    void testCreateTeam_withAdminUser_createsTeamSuccessfully() {
        // Arrange
        CreateTeamRequest request = new CreateTeamRequest("New Team", "Team description");
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        
        TeamEntity savedTeam = new TeamEntity(
            "team-new",
            request.name(),
            request.description(),
            adminUser.id(),
            List.of(adminUser.id()),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(savedTeam);

        // Act
        TeamResponse result = teamService.createTeam(request);

        // Assert
        assertNotNull(result);
        assertEquals("New Team", result.name());
        assertEquals("Team description", result.description());
        assertEquals(adminUser.id(), result.createdBy());
        assertTrue(result.members().contains(adminUser.id()));
        verify(teamRepository, times(1)).save(any(TeamEntity.class));
    }

    // Enrollment&access: RBAC -> Only administrators are allowed to create new teams
    @Test
    void testCreateTeam_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        CreateTeamRequest request = new CreateTeamRequest("New Team", "Team description");
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.createTeam(request)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Only admins can perform this operation"));
        verify(teamRepository, never()).save(any(TeamEntity.class));
    }

    // Enrollment&access: RBAC -> Auto-enroll the creator in the team members list
    @Test
    void testCreateTeam_creatorAutoEnrolledInMembers() {
        // Arrange
        CreateTeamRequest request = new CreateTeamRequest("New Team", "Description");
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        
        TeamEntity savedTeam = new TeamEntity(
            "team-new",
            request.name(),
            request.description(),
            adminUser.id(),
            List.of(adminUser.id()),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(savedTeam);

        // Act
        TeamResponse result = teamService.createTeam(request);

        // Assert
        assertTrue(result.members().contains(adminUser.id()));
    }

    // Enrollment&access: RBAC -> Only administrators can manage membership of any team
    @Test
    void testAddMember_withAdminUser_addMemberSuccessfully() {
        // Arrange
        String teamId = "team-1";
        String newUserId = "user-2";
        AddTeamMemberRequest request = new AddTeamMemberRequest(newUserId);
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));
        
        UserEntity newUser = new UserEntity(newUserId, "New User", "newuser@example.com", "password", "user", Instant.now());
        when(userRepository.findById(newUserId)).thenReturn(Optional.of(newUser));
        
        TeamEntity updatedTeam = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            List.of(adminUser.id(), newUserId),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(updatedTeam);

        // Act
        TeamResponse result = teamService.addMember(teamId, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.members().contains(newUserId));
        assertTrue(result.members().contains(adminUser.id()));
        verify(teamRepository, times(1)).save(any(TeamEntity.class));
    }

    // Enrollment&access: RBAC -> Only administrators can manage membership of any team
    @Test
    void testAddMember_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        AddTeamMemberRequest request = new AddTeamMemberRequest("user-2");
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.addMember(teamId, request)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(teamRepository, never()).save(any(TeamEntity.class));
    }

    // Enrollment&access: RBAC -> Enforce non-null identifiers before repository calls
    @Test
    void testAddMember_withNullTeamId_throwsNullPointerException() {
        // Arrange
        AddTeamMemberRequest request = new AddTeamMemberRequest("user-2");
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            teamService.addMember(null, request)
        );
    }

    // Enrollment&access: RBAC -> Enforce non-null identifiers before repository calls
    @Test
    void testAddMember_withNullUserId_throwsNullPointerException() {
        // Arrange
        AddTeamMemberRequest request = new AddTeamMemberRequest(null);
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            teamService.addMember("team-1", request)
        );
    }

    // Enrollment&access: RBAC -> Validate both team and target user existence before mutating enrollment
    @Test
    void testAddMember_withNonExistentTeam_throwsEntityNotFoundException() {
        // Arrange
        String teamId = "team-999";
        AddTeamMemberRequest request = new AddTeamMemberRequest("user-2");
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
            teamService.addMember(teamId, request)
        );
        assertTrue(exception.getMessage().contains(teamId));
    }

    // Enrollment&access: RBAC -> Validate both team and target user existence before mutating enrollment
    @Test
    void testAddMember_withNonExistentUser_throwsEntityNotFoundException() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-999";
        AddTeamMemberRequest request = new AddTeamMemberRequest(userId);
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
            teamService.addMember(teamId, request)
        );
        assertTrue(exception.getMessage().contains(userId));
    }

    // Enrollment&access: RBAC -> Keep enrollment idempotent by avoiding duplicate member entries
    @Test
    void testAddMember_withAlreadyMember_keepsMemberListWithoutDuplicate() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-1";
        AddTeamMemberRequest request = new AddTeamMemberRequest(userId);
        
        TeamEntity teamWithMultipleMembers = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            new ArrayList<>(List.of(adminUser.id(), userId)),
            Instant.now()
        );
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithMultipleMembers));
        when(userRepository.findById(userId)).thenReturn(Optional.of(regularUser));
        
        TeamEntity savedTeam = new TeamEntity(
            teamWithMultipleMembers.id(),
            teamWithMultipleMembers.name(),
            teamWithMultipleMembers.description(),
            teamWithMultipleMembers.createdBy(),
            new ArrayList<>(List.of(adminUser.id(), userId)),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(savedTeam);

        // Act
        TeamResponse result = teamService.addMember(teamId, request);

        // Assert
        assertEquals(2, result.members().size());
        assertEquals(1, result.members().stream().filter(id -> id.equals(userId)).count());
    }

    // Enrollment&access: RBAC -> Allow access only for admins
    @Test
    void testGetTeamById_withAdminUser_returnsTeam() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));

        // Act
        TeamResponse result = teamService.getTeamById(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(testTeam.id(), result.id());
        assertEquals(testTeam.name(), result.name());
    }

    // Enrollment&access: RBAC -> Allow access for users enrolled in the requested team
    @Test
    void testGetTeamById_withMemberUser_returnsTeam() {
        // Arrange
        String teamId = "team-1";
        UserEntity memberUser = new UserEntity("user-member", "Member User", "member@example.com", "password", "user", Instant.now());
        TeamEntity teamWithMember = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            List.of(adminUser.id(), memberUser.id()),
            Instant.now()
        );
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(memberUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithMember));

        // Act
        TeamResponse result = teamService.getTeamById(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(teamWithMember.id(), result.id());
    }

    // Enrollment&access: RBAC -> Allow access for team owner
    @Test
    void testGetTeamById_withOwnerUser_returnsTeam() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));

        // Act
        TeamResponse result = teamService.getTeamById(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(testTeam.createdBy(), adminUser.id());
    }

    // Enrollment&access: RBAC -> Deny access for unauthorized users
    @Test
    void testGetTeamById_withUnauthorizedUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        UserEntity unauthorizedUser = new UserEntity("user-unauthorized", "Unauthorized", "unauth@example.com", "password", "user", Instant.now());
        when(authContextService.getCurrentUserFromToken()).thenReturn(unauthorizedUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.getTeamById(teamId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // Enrollment&access: RBAC -> Non-existent team returns error
    @Test
    void testGetTeamById_withNonExistentTeam_throwsEntityNotFoundException() {
        // Arrange
        String teamId = "team-999";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
            teamService.getTeamById(teamId)
        );
        assertTrue(exception.getMessage().contains(teamId));
    }

    // Enrollment&access: RBAC -> Admin users can view all teams
    @Test
    void testGetTeamsForCurrentUser_withAdminUser_returnsAllTeams() {
        // Arrange
        List<TeamEntity> allTeams = List.of(
            testTeam,
            new TeamEntity("team-2", "Team 2", "Description 2", "other-admin", List.of("other-admin"), Instant.now())
        );
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findAll()).thenReturn(allTeams);

        // Act
        List<TeamResponse> result = teamService.getTeamsForCurrentUser();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> t.id().equals("team-1")));
        assertTrue(result.stream().anyMatch(t -> t.id().equals("team-2")));
    }

    // Enrollment&access: RBAC -> Regular users only see teams where they are enrolled
    @Test
    void testGetTeamsForCurrentUser_withRegularUser_returnsOnlyEnrolledTeams() {
        // Arrange
        TeamEntity enrolledTeam = new TeamEntity(
            "team-enrolled",
            "Enrolled Team",
            "Description",
            adminUser.id(),
            List.of(adminUser.id(), regularUser.id()),
            Instant.now()
        );
        TeamEntity otherTeam = new TeamEntity(
            "team-other",
            "Other Team",
            "Description",
            "other-admin",
            List.of("other-admin"),
            Instant.now()
        );
        
        List<TeamEntity> allTeams = List.of(enrolledTeam, otherTeam);
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);
        when(teamRepository.findAll()).thenReturn(allTeams);

        // Act
        List<TeamResponse> result = teamService.getTeamsForCurrentUser();

        // Assert
        assertEquals(1, result.size());
        assertEquals("team-enrolled", result.get(0).id());
    }

    // Enrollment&access: RBAC -> Regular users see teams they created
    @Test
    void testGetTeamsForCurrentUser_withUserAsCreator_returnsCreatedTeams() {
        // Arrange
        TeamEntity createdByRegularUser = new TeamEntity(
            "team-created-by-user",
            "Created Team",
            "Description",
            regularUser.id(),
            List.of(regularUser.id()),
            Instant.now()
        );
        
        List<TeamEntity> allTeams = List.of(createdByRegularUser, testTeam);
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);
        when(teamRepository.findAll()).thenReturn(allTeams);

        // Act
        List<TeamResponse> result = teamService.getTeamsForCurrentUser();

        // Assert
        assertEquals(1, result.size());
        assertEquals("team-created-by-user", result.get(0).id());
    }

    // Enrollment&access: RBAC -> Empty team list when user has no enrollment
    @Test
    void testGetTeamsForCurrentUser_withNoEnrollment_returnsEmptyList() {
        // Arrange
        UserEntity isolatedUser = new UserEntity("user-isolated", "Isolated", "isolated@example.com", "password", "user", Instant.now());
        List<TeamEntity> allTeams = List.of(testTeam);
        when(authContextService.getCurrentUserFromToken()).thenReturn(isolatedUser);
        when(teamRepository.findAll()).thenReturn(allTeams);

        // Act
        List<TeamResponse> result = teamService.getTeamsForCurrentUser();

        // Assert
        assertEquals(0, result.size());
    }

    // Enrollment&access: Manage group control -> Allow authenticated users to submit join requests
    @Test
    void testRequestToJoinTeam_withValidTeamId_createsJoinRequest() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));
        
        TeamJoinRequestEntity savedRequest = new TeamJoinRequestEntity(
            "request-1",
            teamId,
            regularUser.id(),
            Instant.now()
        );
        when(teamJoinRequestRepository.save(any(TeamJoinRequestEntity.class))).thenReturn(savedRequest);
        when(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, regularUser.id())).thenReturn(false);

        // Act
        var result = teamService.requestToJoinTeam(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(teamId, result.teamId());
        assertEquals(regularUser.id(), result.userId());
        verify(teamJoinRequestRepository, times(1)).save(any(TeamJoinRequestEntity.class));
    }

    // Enrollment&access: Manage group control -> Prevent redundant requests from already enrolled users
    @Test
    void testRequestToJoinTeam_withAlreadyMember_throwsBadRequestException() {
        // Arrange
        String teamId = "team-1";
        TeamEntity teamWithMember = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            List.of(adminUser.id(), regularUser.id()),
            Instant.now()
        );
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithMember));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.requestToJoinTeam(teamId)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already a team member"));
    }

    // Enrollment&access: Manage group control -> Ensure one pending request per user/team pair
    @Test
    void testRequestToJoinTeam_withExistingRequest_throwsBadRequestException() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));
        when(teamJoinRequestRepository.existsByTeamIdAndUserId(teamId, regularUser.id())).thenReturn(true);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.requestToJoinTeam(teamId)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already exists"));
    }

    // Enrollment&access: Manage group control -> Restrict pending-requests visibility to administrators
    @Test
    void testGetJoinRequests_withAdminUser_returnsPendingRequests() {
        // Arrange
        String teamId = "team-1";
        List<TeamJoinRequestEntity> pendingRequests = List.of(
            new TeamJoinRequestEntity("req-1", teamId, "user-1", Instant.now()),
            new TeamJoinRequestEntity("req-2", teamId, "user-2", Instant.now())
        );
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamJoinRequestRepository.findByTeamId(teamId)).thenReturn(pendingRequests);

        // Act
        var result = teamService.getJoinRequests(teamId);

        // Assert
        assertEquals(2, result.size());
        verify(teamJoinRequestRepository, times(1)).findByTeamId(teamId);
    }

    // Enrollment&access: Manage group control -> Restrict pending-requests visibility to administrators
    @Test
    void testGetJoinRequests_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.getJoinRequests(teamId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // Enrollment&access: Manage group control -> Allow only administrators to approve membership requests
    @Test
    void testApproveJoinRequest_withAdminUser_approvesAndAddsMember() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        TeamJoinRequestEntity joinRequest = new TeamJoinRequestEntity(
            "req-1",
            teamId,
            userId,
            Instant.now()
        );
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(userId)).thenReturn(Optional.of(regularUser));
        when(teamJoinRequestRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(joinRequest));
        
        TeamEntity updatedTeam = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            List.of(adminUser.id(), userId),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(updatedTeam);

        // Act
        TeamResponse result = teamService.approveJoinRequest(teamId, userId);

        // Assert
        assertNotNull(result);
        assertTrue(result.members().contains(userId));
        verify(teamJoinRequestRepository, times(1)).deleteById("req-1");
    }

    // Enrollment&access: Manage group control -> Allow only administrators to approve membership requests
    @Test
    void testApproveJoinRequest_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.approveJoinRequest(teamId, userId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // Enrollment&access: Manage group control -> Validate the requested team, user and pending request before approval
    @Test
    void testApproveJoinRequest_withNonExistentTeam_throwsEntityNotFoundException() {
        // Arrange
        String teamId = "team-999";
        String userId = "user-2";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
            teamService.approveJoinRequest(teamId, userId)
        );
        assertTrue(exception.getMessage().contains(teamId));
    }

    // Enrollment&access: admin controls members/content -> Restrict member-removal operations to administrators
    @Test
    void testRemoveMember_withAdminUser_removesMemberSuccessfully() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        TeamEntity teamWithMembers = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            new ArrayList<>(List.of(adminUser.id(), userId)),
            Instant.now()
        );
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithMembers));
        
        TeamEntity updatedTeam = new TeamEntity(
            testTeam.id(),
            testTeam.name(),
            testTeam.description(),
            testTeam.createdBy(),
            List.of(adminUser.id()),
            Instant.now()
        );
        when(teamRepository.save(any(TeamEntity.class))).thenReturn(updatedTeam);

        // Act
        TeamResponse result = teamService.removeMember(teamId, userId);

        // Assert
        assertNotNull(result);
        assertFalse(result.members().contains(userId));
        verify(teamRepository, times(1)).save(any(TeamEntity.class));
    }

    // Enrollment&access: admin controls members/content -> Protect team ownership from accidental removal
    @Test
    void testRemoveMember_withTeamOwner_throwsBadRequestException() {
        // Arrange
        String teamId = "team-1";
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(testTeam));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.removeMember(teamId, adminUser.id())
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("cannot be removed"));
    }

    // Enrollment&access: admin controls members/content -> Restrict member-removal operations to administrators
    @Test
    void testRemoveMember_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.removeMember(teamId, userId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // Enrollment&access: admin controls members/content -> Restrict rejection of pending requests to administrators
    @Test
    void testRejectJoinRequest_withAdminUser_deletesRequest() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        TeamJoinRequestEntity joinRequest = new TeamJoinRequestEntity(
            "req-1",
            teamId,
            userId,
            Instant.now()
        );
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamJoinRequestRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(joinRequest));

        // Act
        teamService.rejectJoinRequest(teamId, userId);

        // Assert
        verify(teamJoinRequestRepository, times(1)).deleteById("req-1");
    }

    // Enrollment&access: admin controls members/content -> Restrict rejection of pending requests to administrators
    @Test
    void testRejectJoinRequest_withNonAdminUser_throwsForbiddenException() {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            teamService.rejectJoinRequest(teamId, userId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

}
