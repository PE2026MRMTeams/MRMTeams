package ro.unibuc.prodeng.response;

import java.time.Instant;

//Enrollment&access: Expose pending join-request details used in admin review flows.
public record TeamJoinRequestResponse(
        String id,
        String teamId,
        String userId,
        Instant requestedAt
) {
}