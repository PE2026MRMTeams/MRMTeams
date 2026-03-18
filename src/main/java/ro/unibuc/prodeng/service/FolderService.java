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
            
        boolean isAdmin = currentUser.role() != null && "admin".equalsIgnoreCase(currentUser.role());
        boolean isTeamMember = team.members() != null && team.members().contains(currentUser.id());
        
        if (!isAdmin || !isTeamMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to create folders in this team");
        }
        
        FolderEntity folder = new FolderEntity(
                null, 
                request.name(),
                request.teamId(),
                request.parentFolderId(),
                currentUser.id(),
                Instant.now(),
                Instant.now()
        );
        
        FolderEntity saved = folderRepository.save(folder);
        
        if (request.parentFolderId() != null) {
            folderRepository.findById(request.parentFolderId())
                .ifPresent(parent -> {
                    FolderEntity updatedParent = new FolderEntity(
                            parent.id(),
                            parent.name(),
                            parent.teamId(),
                            parent.parentFolderId(),
                            parent.createdBy(),
                            parent.createdAt(),
                            Instant.now() 
                    );
                    folderRepository.save(updatedParent);
                });
        }
        
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
        
        if (!isAdmin || !isTeamMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update folders in this team");
        }
        
        FolderEntity updated = new FolderEntity(
                existing.id(), 
                name, 
                existing.teamId(), 
                existing.parentFolderId(), 
                existing.createdBy(), 
                existing.createdAt(), 
                Instant.now()
        );
        
        FolderEntity saved = folderRepository.save(updated);
        
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
        
        
        deleteFolderRecursively(folderId);
    }

    private void deleteFolderRecursively(String currentFolderId) {
        
        // TODO: delete files in folder
        
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
                folder.createdAt(),
                folder.modifiedAt()
        );
    }
}