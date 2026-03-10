package ro.unibuc.prodeng.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public record UserEntity(
    @Id
    String id,
    String name,
    String email,
    String password,
    String role,
    @CreatedDate 
    Instant createdAt
) {}
