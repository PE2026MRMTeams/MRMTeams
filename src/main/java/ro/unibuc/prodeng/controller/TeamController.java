package ro.unibuc.prodeng.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.AddTeamMemberRequest;
import ro.unibuc.prodeng.request.CreateTeamRequest;
import ro.unibuc.prodeng.response.TeamJoinRequestResponse;
import ro.unibuc.prodeng.response.TeamResponse;
import ro.unibuc.prodeng.service.TeamService;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    @Autowired
    private TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        // Enrollment&access: RBAC -> Admin-only endpoint for creating teams.
        TeamResponse created = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamResponse> addMember(
            @PathVariable String teamId,
            @Valid @RequestBody AddTeamMemberRequest request) {
        // Enrollment&access: RBAC -> Admin-only endpoint for managing team enrollment.
        TeamResponse updated = teamService.addMember(teamId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable String teamId) {
        // Enrollment&access: RBAC -> Team data is returned only for authorized users (admin/member/owner).
        TeamResponse team = teamService.getTeamById(teamId);
        return ResponseEntity.ok(team);
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getMyTeams() {
        // Enrollment&access: RBAC -> Returns teams visible to the current caller based on role and enrollment.
        List<TeamResponse> teams = teamService.getTeamsForCurrentUser();
        return ResponseEntity.ok(teams);
    }

    @PostMapping("/{teamId}/join-requests")
    public ResponseEntity<TeamJoinRequestResponse> requestToJoinTeam(@PathVariable String teamId) {
        //Enrollment&access: Manage group control -> Users can submit controlled join requests instead of direct enrollment.
        TeamJoinRequestResponse createdRequest = teamService.requestToJoinTeam(teamId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRequest);
    }

    @GetMapping("/{teamId}/join-requests")
    public ResponseEntity<List<TeamJoinRequestResponse>> getJoinRequests(@PathVariable String teamId) {
        //Enrollment&access: Manage group control -> Admins can review all pending join requests for one team.
        List<TeamJoinRequestResponse> joinRequests = teamService.getJoinRequests(teamId);
        return ResponseEntity.ok(joinRequests);
    }

    @PostMapping("/{teamId}/join-requests/{userId}/approve")
    public ResponseEntity<TeamResponse> approveJoinRequest(
            @PathVariable String teamId,
            @PathVariable String userId) {
        //Enrollment&access: Manage group control -> Admin approval is required before a user becomes a team member.
        TeamResponse updatedTeam = teamService.approveJoinRequest(teamId, userId);
        return ResponseEntity.ok(updatedTeam);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<TeamResponse> removeMember(
            @PathVariable String teamId,
            @PathVariable String userId) {
        //Enrollment&access: admin controls members/content -> Admins can remove users from group membership.
        TeamResponse updatedTeam = teamService.removeMember(teamId, userId);
        return ResponseEntity.ok(updatedTeam);
    }

    @DeleteMapping("/{teamId}/join-requests/{userId}")
    public ResponseEntity<Void> rejectJoinRequest(
            @PathVariable String teamId,
            @PathVariable String userId) {
        //Enrollment&access: admin controls members/content -> Admins can reject pending join requests.
        teamService.rejectJoinRequest(teamId, userId);
        return ResponseEntity.noContent().build();
    }
}