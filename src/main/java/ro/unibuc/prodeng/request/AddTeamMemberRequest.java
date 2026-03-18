package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record AddTeamMemberRequest(
        @NotBlank(message = "User id is required")
        String userId
) {
}