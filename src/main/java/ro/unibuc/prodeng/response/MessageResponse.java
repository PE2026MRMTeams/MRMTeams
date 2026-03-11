package ro.unibuc.prodeng.response;


import java.time.Instant;

//Enrollment&access: admin controls members/content -> Safe response model for message data shown to authorized team users.
public record MessageResponse(
        String id,
        String content,
        String teamId,
        String sentBy,
        Instant sentAt
) {
    
}
