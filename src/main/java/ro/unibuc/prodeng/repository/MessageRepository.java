package ro.unibuc.prodeng.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.MessageEntity;

@Repository
//Enrollment&access: admin controls members/content -> Repository contract for group message moderation operations.
public interface MessageRepository extends MongoRepository<MessageEntity, String> {
    List<MessageEntity> findByTeamId(String teamId);

    Optional<MessageEntity> findByIdAndTeamId(String id, String teamId);
}