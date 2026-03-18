package ro.unibuc.prodeng.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ro.unibuc.prodeng.model.FolderEntity;

@Repository
public interface FolderRepository extends MongoRepository<FolderEntity, String> {

    List<FolderEntity> findByTeamId(String teamId);
    
    List<FolderEntity> findByParentFolderId(String parentFolderId);
    
}