package ro.unibuc.prodeng.repository;

// public class FolderRepository {
    
// }

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ro.unibuc.prodeng.model.FolderEntity;

@Repository
public interface FolderRepository extends MongoRepository<FolderEntity, String> {

    Optional<FolderEntity> findByTeamId(String id);
}