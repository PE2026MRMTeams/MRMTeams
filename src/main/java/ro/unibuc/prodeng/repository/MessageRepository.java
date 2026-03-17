package ro.unibuc.prodeng.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.MessageEntity;

@Repository
public interface MessageRepository extends MongoRepository<MessageEntity, String> {

    List<MessageEntity> findByTeamIdOrderBySentAtDescIdDesc(String teamId, Pageable pageable);
    List<MessageEntity> findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(String teamId, Instant cursorSentAt, Pageable pageable);

    @Query("{ 'teamId': ?0, '$or': [ { 'sentAt': { '$lt': ?1 } }, { 'sentAt': ?1, '_id': { '$lt': ?2 } } ] }")
    List<MessageEntity> findPageByTeamIdFromCursor(String teamId, Instant cursorSentAt, String cursorId, Pageable pageable);
    Optional<MessageEntity> findByIdAndTeamId(String id, String teamId);
}