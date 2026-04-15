package ro.unibuc.prodeng.projection;

import java.time.Instant;

public record MessagePreviewProjection(
        String id,
        String content,
        String teamId,
        String sentBy,
        Instant sentAt,
        boolean truncated
) {
}

