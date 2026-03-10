package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

// Enrollment&access: RBAC -> Payload used by admins to enroll a user into a team.
public record AddTeamMemberRequest(
        @NotBlank(message = "User id is required")
        String userId
) {
}