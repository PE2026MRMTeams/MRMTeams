package ro.unibuc.prodeng.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.MessageEntity;
import ro.unibuc.prodeng.projection.MessagePreviewProjection;

@Repository
public interface MessageRepository extends MongoRepository<MessageEntity, String> {

    List<MessageEntity> findByTeamIdOrderBySentAtDescIdDesc(String teamId, Pageable pageable);
    List<MessageEntity> findByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(String teamId, Instant cursorSentAt, Pageable pageable);

    @Aggregation(pipeline = {
            "{ '$match': { 'teamId': ?0 } }",
            "{ '$sort': { 'sentAt': -1, '_id': -1 } }",
            "{ '$limit': ?1 }",
            "{ '$project': { 'content': { '$substrCP': [ { '$ifNull': ['$content', ''] }, 0, ?2 ] }, 'teamId': 1, 'sentBy': 1, 'sentAt': 1, 'truncated': { '$gt': [ { '$strLenCP': { '$ifNull': ['$content', ''] } }, ?2 ] } } }"
    })
    List<MessagePreviewProjection> findPreviewPageByTeamIdOrderBySentAtDescIdDesc(String teamId, int limit, int previewLimit);

    @Aggregation(pipeline = {
            "{ '$match': { 'teamId': ?0, '$or': [ { 'sentAt': { '$lt': ?1 } }, { 'sentAt': ?1, '_id': { '$lt': ?2 } } ] } }",
            "{ '$sort': { 'sentAt': -1, '_id': -1 } }",
            "{ '$limit': ?3 }",
            "{ '$project': { 'content': { '$substrCP': [ { '$ifNull': ['$content', ''] }, 0, ?4 ] }, 'teamId': 1, 'sentBy': 1, 'sentAt': 1, 'truncated': { '$gt': [ { '$strLenCP': { '$ifNull': ['$content', ''] } }, ?4 ] } } }"
    })
    List<MessagePreviewProjection> findPreviewPageByTeamIdFromCursor(String teamId, Instant cursorSentAt, String cursorId, int limit, int previewLimit);

    @Aggregation(pipeline = {
            "{ '$match': { 'teamId': ?0, 'sentAt': { '$lt': ?1 } } }",
            "{ '$sort': { 'sentAt': -1, '_id': -1 } }",
            "{ '$limit': ?2 }",
            "{ '$project': { 'content': { '$substrCP': [ { '$ifNull': ['$content', ''] }, 0, ?3 ] }, 'teamId': 1, 'sentBy': 1, 'sentAt': 1, 'truncated': { '$gt': [ { '$strLenCP': { '$ifNull': ['$content', ''] } }, ?3 ] } } }"
    })
    List<MessagePreviewProjection> findPreviewPageByTeamIdAndSentAtBeforeOrderBySentAtDescIdDesc(String teamId, Instant cursorSentAt, int limit, int previewLimit);

    Optional<MessageEntity> findByIdAndTeamId(String id, String teamId);
}