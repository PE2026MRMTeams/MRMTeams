package ro.unibuc.prodeng.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.TeamEntity;

@Repository
// Enrollment&access: RBAC -> Repository boundary for team enrollment persistence.
public interface TeamRepository extends MongoRepository<TeamEntity, String> {
}