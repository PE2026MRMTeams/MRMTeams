package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.response.FolderResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.FolderRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.request.CreateFolderRequest;

@Service
public class FolderService {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuthContextService authContextService;

    public List<FolderResponse> getAllFolders(String teamId) throws EntityNotFoundException {
        String requiredTeamId = Objects.requireNonNull(teamId, "TeamId is required");

        UserEntity currentUser = authContextService.getCurrentUserFromToken();

        TeamEntity team = teamRepository.findById(requiredTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

        boolean isAdmin = currentUser.role() != null && "admin".equalsIgnoreCase(currentUser.role());
        boolean isTeamOwner = team.createdBy() != null && team.createdBy().equals(currentUser.id());
        boolean isMember = team.members() != null && team.members().contains(currentUser.id());
        
        if (!isAdmin && !isTeamOwner && !isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this team's folders");
        }

        return folderRepository.findByTeamId(requiredTeamId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<FolderResponse> getSubFolders(String folderId) throws EntityNotFoundException {
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        
        FolderEntity parentFolder = folderRepository.findById(folderId)
                .orElseThrow(() -> new EntityNotFoundException("Folder not found: " + folderId));
        boolean isMember = teamRepository.findById(parentFolder.teamId())
            .map(team -> team.members() != null && team.members().contains(currentUser.id()))
            .orElse(false);
            
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this folder's subfolders");
        }
        
        return folderRepository.findByParentFolderId(folderId).stream()
                .map(this::toResponse)
                .toList();
    }

    public FolderResponse createFolder(CreateFolderRequest request) throws EntityNotFoundException {
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        String teamId = request.teamId();
        
        TeamEntity team = teamRepository.findById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + teamId));

        if (request.parentFolderId() != null) {
            FolderEntity parentFolder = folderRepository.findById(request.parentFolderId())
                .orElseThrow(() -> new EntityNotFoundException("Parent folder not found: " + request.parentFolderId())); 

            //Unique Name validation for subfolders
            boolean nameExists = folderRepository.findByParentFolderId(request.parentFolderId()).stream()
                .anyMatch(folder -> folder.name().equalsIgnoreCase(request.name()));
            if (nameExists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A subfolder with the same name already exists in the parent folder");
            }
        }
        //Unique Name validation for root folders
        if (request.parentFolderId() == null) {
            boolean nameExists = folderRepository.findByTeamId(teamId).stream()
                .filter(folder -> folder.parentFolderId() == null)
                .anyMatch(folder -> folder.name().equalsIgnoreCase(request.name()));
            if (nameExists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A root folder with the same name already exists in the team");
            }
        }



        boolean isAdmin = currentUser.role() != null && "admin".equalsIgnoreCase(currentUser.role());
        boolean isTeamMember = team.members() != null && team.members().contains(currentUser.id());
        
        if (!isAdmin || !isTeamMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to create folders in this team");
        }
        
        Instant now = Instant.now();

        FolderEntity folder = new FolderEntity(
                null, 
                request.name(),
                request.teamId(),
                request.parentFolderId(),
                currentUser.id(),
                now,
                now
        );
        
        FolderEntity saved = folderRepository.save(folder);
        
        updateParentFolderModifiedAtRecursively(request.parentFolderId(), now);
        
        return toResponse(saved);
    }

    public FolderResponse updateFolderName(String folderId, String name) throws EntityNotFoundException {
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        
        FolderEntity existing = folderRepository.findById(folderId)
                .orElseThrow(() -> new EntityNotFoundException(folderId));
                
        TeamEntity team = teamRepository.findById(existing.teamId())
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + existing.teamId()));
            
        boolean isAdmin = currentUser.role() != null && "admin".equalsIgnoreCase(currentUser.role());
        boolean isTeamMember = team.members() != null && team.members().contains(currentUser.id());
        

        //Check for unique name among siblings
        List<FolderEntity> siblingFolders = existing.parentFolderId() == null
            ? folderRepository.findByTeamId(existing.teamId()).stream().filter(folder -> folder.parentFolderId() == null).toList()
            : folderRepository.findByParentFolderId(existing.parentFolderId());
        boolean nameExists = siblingFolders.stream().filter(folder -> !folder.id().equals(folderId)) 
            .anyMatch(folder -> folder.name().equalsIgnoreCase(name));
        if (nameExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder with the same name already exists in the same location");
        }

        if (!isAdmin || !isTeamMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update folders in this team");
        }
        
        Instant now = Instant.now();
        FolderEntity updated = new FolderEntity(
                existing.id(), 
                name, 
                existing.teamId(), 
                existing.parentFolderId(), 
                existing.createdBy(), 
                existing.createdAt(), 
                now
        );
        
        FolderEntity saved = folderRepository.save(updated);
        
        updateParentFolderModifiedAtRecursively(existing.parentFolderId(), now);

        return toResponse(saved);
    }

    public void deleteFolder(String folderId) throws EntityNotFoundException {
        UserEntity currentUser = authContextService.getCurrentUserFromToken();
        
        FolderEntity existing = folderRepository.findById(folderId)
                .orElseThrow(() -> new EntityNotFoundException(folderId));

        TeamEntity team = teamRepository.findById(existing.teamId())
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + existing.teamId()));

        boolean isAdmin = currentUser.role() != null && "admin".equalsIgnoreCase(currentUser.role());
        boolean isTeamMember = team.members() != null && team.members().contains(currentUser.id());
        
        if (!isAdmin || !isTeamMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete folders in this team");
        }
        
        Instant now = Instant.now();
        updateParentFolderModifiedAtRecursively(existing.parentFolderId(), now);
        deleteFolderRecursively(folderId);
    }

    private void updateParentFolderModifiedAtRecursively(String parentFolderId, Instant modifiedAt) {
         while (parentFolderId != null) {
        FolderEntity parent = folderRepository.findById(parentFolderId)
            .orElse(null);

        if (parent == null) break;

        FolderEntity updatedParent = new FolderEntity(
                parent.id(),
                parent.name(),
                parent.teamId(),
                parent.parentFolderId(),
                parent.createdBy(),
                parent.createdAt(),
                modifiedAt
        );

        folderRepository.save(updatedParent);

        parentFolderId = parent.parentFolderId();
    }
    }

    private void deleteFolderRecursively(String currentFolderId) {
        
        // TODO: delete files in folder
        if(currentFolderId == null) {
            return;
        }

        List<FolderEntity> subfolders = folderRepository.findByParentFolderId(currentFolderId);
        
        
        for (FolderEntity subfolder : subfolders) {
            deleteFolderRecursively(subfolder.id());
        }
        
        
        folderRepository.deleteById(currentFolderId);
    }

    private FolderResponse toResponse(FolderEntity folder) {
        return new FolderResponse(
                folder.id(),
                folder.name(),
                teamRepository.findById(folder.teamId()).map(TeamEntity::name).orElse("Unknown Team"),
                folder.parentFolderId() != null ? folderRepository.findById(folder.parentFolderId()).map(FolderEntity::name).orElse("Unknown Folder") : null,
                folder.createdAt(),
                folder.modifiedAt()
        );
    }
}