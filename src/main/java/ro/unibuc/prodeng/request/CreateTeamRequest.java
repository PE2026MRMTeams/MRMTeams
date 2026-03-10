package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

// Enrollment&access: RBAC -> Payload used by admins to define a new team.
public record CreateTeamRequest(
        @NotBlank(message = "Team name is required")
        String name,
        String description
) {
}