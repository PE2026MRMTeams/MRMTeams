package ro.unibuc.prodeng.response;

import java.time.Instant;
import java.util.List;

// Enrollment&access: RBAC -> Read model exposing enrollment-relevant team data.
public record TeamResponse(
        String id,
        String name,
        String description,
        String createdBy,
        List<String> members,
        Instant modifiedAt
) {
}