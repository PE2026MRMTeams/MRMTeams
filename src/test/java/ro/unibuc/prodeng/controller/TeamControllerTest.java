package ro.unibuc.prodeng.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ro.unibuc.prodeng.request.AddTeamMemberRequest;
import ro.unibuc.prodeng.request.CreateTeamRequest;
import ro.unibuc.prodeng.response.TeamResponse;
import ro.unibuc.prodeng.service.TeamService;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamController teamController;

    private CreateTeamRequest createTeamRequest;
    private AddTeamMemberRequest addTeamMemberRequest;
    private TeamResponse teamResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(teamController).build();
        createTeamRequest = new CreateTeamRequest("Test Team", "A test team");
        addTeamMemberRequest = new AddTeamMemberRequest("user-2");
        teamResponse = new TeamResponse(
            "team-1",
            "Test Team",
            "A test team",
            "admin-1",
            List.of("admin-1"),
            Instant.now()
        );
    }

    // Enrollment&access: RBAC -> Admin-only endpoint for creating teams
    @Test
    void testCreateTeam_withValidRequest_returnsCreatedStatus() throws Exception {
        // Arrange
        when(teamService.createTeam(any(CreateTeamRequest.class))).thenReturn(teamResponse);

        // Act & Assert
        mockMvc.perform(post("/api/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTeamRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("team-1"))
                .andExpect(jsonPath("$.name").value("Test Team"));

        verify(teamService, times(1)).createTeam(any(CreateTeamRequest.class));
    }

    // Enrollment&access: RBAC -> Endpoint validates request body
    @Test
    void testCreateTeam_withEmptyTeamName_returnsBadRequest() throws Exception {
        // Arrange
        CreateTeamRequest invalidRequest = new CreateTeamRequest("", "Description");

        // Act & Assert
        mockMvc.perform(post("/api/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(teamService, never()).createTeam(any(CreateTeamRequest.class));
    }

    // Enrollment&access: RBAC -> Admin-only endpoint for managing team enrollment
    @Test
    void testAddMember_withValidRequest_returnsOkStatus() throws Exception {
        // Arrange
        String teamId = "team-1";
        TeamResponse updatedTeam = new TeamResponse(
            teamId,
            teamResponse.name(),
            teamResponse.description(),
            teamResponse.createdBy(),
            List.of("admin-1", "user-2"),
            Instant.now()
        );
        when(teamService.addMember(teamId, addTeamMemberRequest)).thenReturn(updatedTeam);

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/members", teamId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addTeamMemberRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teamId))
                .andExpect(jsonPath("$.members.length()").value(2));

        verify(teamService, times(1)).addMember(teamId, addTeamMemberRequest);
    }

    // Enrollment&access: RBAC -> Endpoint validates user id is not blank
    @Test
    void testAddMember_withEmptyUserId_returnsBadRequest() throws Exception {
        // Arrange
        String teamId = "team-1";
        AddTeamMemberRequest invalidRequest = new AddTeamMemberRequest("");

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/members", teamId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(teamService, never()).addMember(anyString(), any(AddTeamMemberRequest.class));
    }

    // Enrollment&access: RBAC -> Team data is returned only for authorized users
    @Test
    void testGetTeamById_withValidTeamId_returnsTeamData() throws Exception {
        // Arrange
        String teamId = "team-1";
        when(teamService.getTeamById(teamId)).thenReturn(teamResponse);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}", teamId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("team-1"))
                .andExpect(jsonPath("$.name").value("Test Team"))
                .andExpect(jsonPath("$.createdBy").value("admin-1"));

        verify(teamService, times(1)).getTeamById(teamId);
    }

    // Enrollment&access: RBAC -> Team data is returned only for authorized users
    @Test
    void testGetTeamById_withTeamMember_returnsTeamData() throws Exception {
        // Arrange
        String teamId = "team-1";
        TeamResponse memberTeam = new TeamResponse(
            teamId,
            "Test Team",
            "A test team",
            "admin-1",
            List.of("admin-1", "user-2"),
            Instant.now()
        );
        when(teamService.getTeamById(teamId)).thenReturn(memberTeam);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        verify(teamService, times(1)).getTeamById(teamId);
    }

    // Enrollment&access: RBAC -> Returns teams visible to the current caller
    @Test
    void testGetMyTeams_withUserEnrolledInTeams_returnsTeams() throws Exception {
        // Arrange
        List<TeamResponse> userTeams = List.of(
            teamResponse,
            new TeamResponse("team-2", "Team 2", "Description", "admin-1", List.of("admin-1", "user-1"), Instant.now())
        );
        when(teamService.getTeamsForCurrentUser()).thenReturn(userTeams);

        // Act & Assert
        mockMvc.perform(get("/api/teams")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("team-1"))
                .andExpect(jsonPath("$[1].id").value("team-2"));

        verify(teamService, times(1)).getTeamsForCurrentUser();
    }

    // Enrollment&access: RBAC -> Returns empty list when user has no teams
    @Test
    void testGetMyTeams_withNoEnrolledTeams_returnsEmptyList() throws Exception {
        // Arrange
        when(teamService.getTeamsForCurrentUser()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(teamService, times(1)).getTeamsForCurrentUser();
    }

    // Enrollment&access: RBAC -> Regular users see only their teams, admins see all
    @Test
    void testGetMyTeams_respectsUserEnrollmentBasedFiltering() throws Exception {
        // Arrange
        List<TeamResponse> visibleTeams = List.of(teamResponse);
        when(teamService.getTeamsForCurrentUser()).thenReturn(visibleTeams);

        // Act & Assert
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("team-1"));

        verify(teamService, times(1)).getTeamsForCurrentUser();
    }

    // Enrollment&access: Manage group control -> Users can submit controlled join requests
    @Test
    void testRequestToJoinTeam_withValidTeamId_returnsCreatedStatus() throws Exception {
        // Arrange
        String teamId = "team-1";
        var joinResponse = new ro.unibuc.prodeng.response.TeamJoinRequestResponse(
            "req-1",
            teamId,
            "user-1",
            Instant.now()
        );
        when(teamService.requestToJoinTeam(teamId)).thenReturn(joinResponse);

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/join-requests", teamId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.userId").value("user-1"));

        verify(teamService, times(1)).requestToJoinTeam(teamId);
    }

    // Enrollment&access: Manage group control -> Admins can review all pending join requests for one team
    @Test
    void testGetJoinRequests_withValidTeamId_returnsRequests() throws Exception {
        // Arrange
        String teamId = "team-1";
        List<ro.unibuc.prodeng.response.TeamJoinRequestResponse> requests = List.of(
            new ro.unibuc.prodeng.response.TeamJoinRequestResponse("req-1", teamId, "user-1", Instant.now()),
            new ro.unibuc.prodeng.response.TeamJoinRequestResponse("req-2", teamId, "user-2", Instant.now())
        );
        when(teamService.getJoinRequests(teamId)).thenReturn(requests);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/join-requests", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value("user-1"))
                .andExpect(jsonPath("$[1].userId").value("user-2"));

        verify(teamService, times(1)).getJoinRequests(teamId);
    }

    // Enrollment&access: Manage group control -> Admin approval is required before a user becomes a team member
    @Test
    void testApproveJoinRequest_withValidRequest_approvesAndReturnsMember() throws Exception {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        TeamResponse updatedTeam = new TeamResponse(
            teamId,
            "Test Team",
            "A test team",
            "admin-1",
            List.of("admin-1", userId),
            Instant.now()
        );
        when(teamService.approveJoinRequest(teamId, userId)).thenReturn(updatedTeam);

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/join-requests/{userId}/approve", teamId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1]").value(userId));

        verify(teamService, times(1)).approveJoinRequest(teamId, userId);
    }

    // Enrollment&access: admin controls members/content -> Admins can remove users from group membership
    @Test
    void testRemoveMember_withValidRequest_removesMemberSuccessfully() throws Exception {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";
        TeamResponse updatedTeam = new TeamResponse(
            teamId,
            "Test Team",
            "A test team",
            "admin-1",
            List.of("admin-1"),
            Instant.now()
        );
        when(teamService.removeMember(teamId, userId)).thenReturn(updatedTeam);

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/members/{userId}", teamId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0]").value("admin-1"));

        verify(teamService, times(1)).removeMember(teamId, userId);
    }
    
    // Enrollment&access: admin controls members/content -> Admins can reject pending join requests
    @Test
    void testRejectJoinRequest_withValidRequest_returnsNoContent() throws Exception {
        // Arrange
        String teamId = "team-1";
        String userId = "user-2";

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/join-requests/{userId}", teamId, userId))
                .andExpect(status().isNoContent());

        verify(teamService, times(1)).rejectJoinRequest(teamId, userId);
    }

}

