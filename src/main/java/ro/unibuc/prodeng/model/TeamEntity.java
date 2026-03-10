package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document(collection = "teams")
public record TeamEntity(
    @Id String id,
    String name,
    String description,
    String createdBy, // userId
    List<String> members, // list[userId]
    @CreatedDate 
    Instant modifiedAt 
) {}