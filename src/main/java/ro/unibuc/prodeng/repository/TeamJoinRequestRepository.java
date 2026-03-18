package ro.unibuc.prodeng.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.TeamJoinRequestEntity;

@Repository
public interface TeamJoinRequestRepository extends MongoRepository<TeamJoinRequestEntity, String> {

    List<TeamJoinRequestEntity> findByTeamId(String teamId);
    Optional<TeamJoinRequestEntity> findByTeamIdAndUserId(String teamId, String userId);

    boolean existsByTeamIdAndUserId(String teamId, String userId);
}