package ro.unibuc.prodeng.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.TeamEntity;

@Repository
public interface TeamRepository extends MongoRepository<TeamEntity, String> {
}