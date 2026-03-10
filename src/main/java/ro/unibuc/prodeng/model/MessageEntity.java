package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;


@Document(collection = "messages")
public record MessageEntity(
    @Id String id,
    String content,
    String teamId,       
    String sentBy,       
    @CreatedDate 
    Instant sentAt
) {}