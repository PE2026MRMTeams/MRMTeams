package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;



@Document(collection = "files")
public record FileEntity(
    @Id String id,
    String name,
    String folderId,
    String teamId, 
    String createdBy,
    String gridFsId, //ref to the real content from fs.chunks (GridFS)
    String contentType,   // eg: "image/png", "application/pdf"
    long size,//In the .http you provide the relative or absolute path to the local file using the < symbol and Rest Client creates the bytes.
    @CreatedDate 
    Instant createdAt,
    @LastModifiedDate 
    Instant modifiedAt
) {}