package ro.unibuc.prodeng.response;

import java.time.Instant;


public record FolderResponse(
    String id,
    String name,
    Instant createdAt,
    Instant modifiedAt
) {}
