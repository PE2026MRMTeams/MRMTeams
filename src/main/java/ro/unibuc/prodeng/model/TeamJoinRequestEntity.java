package ro.unibuc.prodeng.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "team_join_requests")
//Enrollment&access: Manage group control -> Persist pending join intents until an admin explicitly approves them.
public record TeamJoinRequestEntity(
        @Id String id,
        String teamId,
        String userId,
        @CreatedDate Instant requestedAt
) {
}