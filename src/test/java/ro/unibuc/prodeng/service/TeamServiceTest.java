package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

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

@ExtendWith(SpringExtension.class)
@SuppressWarnings("null")
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

    @Test
    void testCreateTeam_adminUserProvided_createsTeam() {
        // Enrollment&access: RBAC -> Configure an admin caller and assert team creation is allowed.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.save(org.mockito.ArgumentMatchers.<TeamEntity>notNull())).thenAnswer(invocation -> {
            TeamEntity value = invocation.getArgument(0);
            return new TeamEntity("team-1", value.name(), value.description(), value.createdBy(), value.members(), value.modifiedAt());
        });

        TeamResponse result = teamService.createTeam(new CreateTeamRequest("Engineering", "Main team"));

        assertEquals("team-1", result.id());
        assertEquals("Engineering", result.name());
        assertEquals(List.of("admin-id"), result.members());
        verify(teamRepository).save(org.mockito.ArgumentMatchers.<TeamEntity>notNull());
    }

    @Test
    void testCreateTeam_nonAdminUserProvided_throwsForbidden() {
        // Enrollment&access: RBAC -> Reject team creation when caller role is not admin.
        UserEntity regularUser = new UserEntity("user-id", "User", "user@uni.ro", "pass", "user", Instant.now());
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> teamService.createTeam(new CreateTeamRequest("Team", "Desc")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testAddMember_nonAdminUserProvided_throwsForbidden() {
        // Enrollment&access: RBAC -> Reject enrollment mutations when caller is not admin.
        UserEntity regularUser = new UserEntity("user-id", "User", "user@uni.ro", "pass", "user", Instant.now());
        when(authContextService.getCurrentUserFromToken()).thenReturn(regularUser);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> teamService.addMember("team-1", new AddTeamMemberRequest("another-user")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testGetTeamById_nonMemberAndNonAdmin_throwsForbidden() {
        // Enrollment&access: RBAC -> Deny team read for callers that are neither admin nor enrolled members.
        UserEntity outsider = new UserEntity("user-id", "User", "user@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "owner-id", new ArrayList<>(List.of("member-id")), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(outsider);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> teamService.getTeamById("team-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testRequestToJoinTeam_memberAlreadyEnrolled_throwsBadRequest() {
        //Enrollment&access: Manage group control -> Reject join requests when the caller is already a team member.
        UserEntity member = new UserEntity("member-id", "Member", "member@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", List.of("member-id"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(member);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> teamService.requestToJoinTeam("team-1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testRequestToJoinTeam_validCaller_createsPendingRequest() {
        //Enrollment&access: Manage group control -> Create one pending join request for eligible non-member callers.
        UserEntity caller = new UserEntity("user-1", "User", "user@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", List.of("member-id"), Instant.now());
        TeamJoinRequestEntity saved = new TeamJoinRequestEntity("req-1", "team-1", "user-1", Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(caller);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamJoinRequestRepository.existsByTeamIdAndUserId("team-1", "user-1")).thenReturn(false);
        when(teamJoinRequestRepository.save(org.mockito.ArgumentMatchers.<TeamJoinRequestEntity>notNull())).thenReturn(saved);

        TeamJoinRequestResponse result = teamService.requestToJoinTeam("team-1");

        assertEquals("req-1", result.id());
        assertEquals("team-1", result.teamId());
        assertEquals("user-1", result.userId());
    }

    @Test
    void testApproveJoinRequest_adminCaller_addsMemberAndDeletesPendingRequest() {
        //Enrollment&access: Manage group control -> Approving a pending request enrolls the user and removes the request.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        UserEntity targetUser = new UserEntity("user-1", "User", "user@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", new ArrayList<>(List.of("member-id")), Instant.now());
        TeamJoinRequestEntity joinRequest = new TeamJoinRequestEntity("req-1", "team-1", "user-1", Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(targetUser));
        when(teamJoinRequestRepository.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(joinRequest));
        when(teamRepository.save(org.mockito.ArgumentMatchers.<TeamEntity>notNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse result = teamService.approveJoinRequest("team-1", "user-1");

        assertEquals("team-1", result.id());
        assertEquals(true, result.members().contains("user-1"));
        verify(teamJoinRequestRepository).deleteById("req-1");
    }

    @Test
    void testRemoveMember_adminCaller_removesUserFromTeam() {
        //Enrollment&access: admin controls members/content -> Allow admins to remove existing group members.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "admin-id", new ArrayList<>(List.of("member-id", "user-1")), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(teamRepository.save(org.mockito.ArgumentMatchers.<TeamEntity>notNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse result = teamService.removeMember("team-1", "user-1");

        assertEquals(false, result.members().contains("user-1"));
    }

    @Test
    void testRejectJoinRequest_adminCaller_deletesPendingRequest() {
        //Enrollment&access: admin controls members/content -> Allow admins to reject pending join requests.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        TeamJoinRequestEntity joinRequest = new TeamJoinRequestEntity("req-1", "team-1", "user-1", Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamJoinRequestRepository.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(joinRequest));

        teamService.rejectJoinRequest("team-1", "user-1");

        verify(teamJoinRequestRepository).deleteById("req-1");
    }
}