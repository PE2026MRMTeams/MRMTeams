package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.FolderRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.request.CreateFolderRequest;
import ro.unibuc.prodeng.response.FolderResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AuthContextService authContextService;

    @InjectMocks
    private FolderService folderService;

    @Test
    void testGetAllFolders_whenUserIsAdmin_returnsFolders() throws EntityNotFoundException {
        // Arrange
        String teamId = "team-1";
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@prodeng.ro", "password", "admin", Instant.now());
        TeamEntity team = new TeamEntity(teamId, "Backend Team", "Desc", "creator-id", List.of("user-1"), Instant.now());
        FolderEntity folder = new FolderEntity("folder-1", "Project1", teamId, null, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId(teamId)).thenReturn(List.of(folder));

        // Act
        List<FolderResponse> result = folderService.getAllFolders(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("folder-1", result.get(0).id());
        assertEquals("Project1", result.get(0).name());
        verify(folderRepository, times(1)).findByTeamId(teamId);
    }

    @Test
    void testGetAllFolders_whenTeamNotFound_throwsEntityNotFoundException() {
        // Arrange
        String invalidTeamId = "invalid-id";
        UserEntity user = new UserEntity("user-1", "Junior", "junior@prodeng.ro", "password", "user", Instant.now());
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(user);
        when(teamRepository.findById(invalidTeamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> folderService.getAllFolders(invalidTeamId));
        verify(folderRepository, never()).findByTeamId(anyString());
    }

    @Test
    void testCreateFolder_withValidRootFolder_createsAndReturnsFolder() throws EntityNotFoundException {
        // Arrange
        CreateFolderRequest request = new CreateFolderRequest("RootFolder", "team-1", null);
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        
        FolderEntity savedEntity = new FolderEntity("folder-1", "RootFolder", "team-1", null, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId("team-1")).thenReturn(List.of()); // No existing folders
        when(folderRepository.save(any(FolderEntity.class))).thenReturn(savedEntity);

        // Act
        FolderResponse result = folderService.createFolder(request);

        // Assert
        assertNotNull(result);
        assertEquals("folder-1", result.id());
        assertEquals("RootFolder", result.name());
        verify(folderRepository, times(1)).save(any(FolderEntity.class));
    }

    @Test
    void testDeleteFolder_whenUserLacksPermissions_throwsResponseStatusException() {
        // Arrange
        String folderId = "folder-1";
        UserEntity normalUser = new UserEntity("user-2", "Junior", "junior@test.ro", "pass", "user", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Docs", "team-1", null, "creator", Instant.now(), Instant.now());
        // User is not a member of the team and not an admin
        TeamEntity team = new TeamEntity("team-1", "Team", "Desc", "creator", List.of("other-user"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(normalUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.deleteFolder(folderId));
        verify(folderRepository, never()).deleteById(anyString());
    }
}