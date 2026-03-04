package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "folders") public record FolderEntity(
    @Id String id,
    String teamId,
    String name,
    String parentFolderId,
    String createdBy,
    
    @CreatedDate 
    Instant createdAt,
    @CreatedDate 
    Instant modifiedAt 
) {}