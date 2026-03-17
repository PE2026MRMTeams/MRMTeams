package ro.unibuc.prodeng.response;


import java.time.Instant;

public record MessageResponse(
        String id,
        String content,
        String teamId,
        String sentBy,
        Instant sentAt,
        boolean isTruncated
) {
    
}
