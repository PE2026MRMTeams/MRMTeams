package ro.unibuc.prodeng.response;

import java.time.Instant;
import java.util.List;

public record TeamResponse(
        String id,
        String name,
        String description,
        String createdBy,
        List<String> members,
        Instant modifiedAt
) {
}