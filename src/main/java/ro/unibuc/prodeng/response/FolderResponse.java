package ro.unibuc.prodeng.response;

import java.time.Instant;


public record FolderResponse(
    String id,
    String name,
    String teamName,
    String parentFolderName,
    Instant createdAt,
    Instant modifiedAt
) {}
