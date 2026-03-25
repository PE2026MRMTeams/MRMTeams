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


    @Test
    void testCreateFolder_rootFolderNameAlreadyExists_throwsBadRequest() {
        // Arrange
        CreateFolderRequest request = new CreateFolderRequest("DuplicateName", "team-1", null);
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        
        FolderEntity existingFolder = new FolderEntity("folder-99", "DuplicateName", "team-1", null, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId("team-1")).thenReturn(List.of(existingFolder));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.createFolder(request));
        
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void testUpdateFolderName_withValidData_updatesAndReturnsFolder() throws EntityNotFoundException {
        // Arrange
        String folderId = "folder-1";
        String newName = "Updated Project";
        UserEntity memberUser = new UserEntity("user-1", "Member", "member@test.ro", "pass", "admin", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Old Name", "team-1", null, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(memberUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId("team-1")).thenReturn(List.of(existingFolder));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        FolderResponse result = folderService.updateFolderName(folderId, newName);

        // Assert
        assertNotNull(result);
        assertEquals(newName, result.name());
        verify(folderRepository, times(1)).save(any(FolderEntity.class));
    }

    @Test
    void testGetSubFolders_whenUserIsMember_returnsSubFolders() throws EntityNotFoundException {
        // Arrange
        String folderId = "folder-parent";
        UserEntity memberUser = new UserEntity("user-1", "Member", "member@test.ro", "pass", "user", Instant.now());
        FolderEntity parentFolder = new FolderEntity(folderId, "Parent", "team-1", null, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        FolderEntity childFolder = new FolderEntity("folder-child", "Child", "team-1", folderId, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(memberUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(parentFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByParentFolderId(folderId)).thenReturn(List.of(childFolder));

        // Act
        List<FolderResponse> result = folderService.getSubFolders(folderId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("folder-child", result.get(0).id());
        assertEquals("Child", result.get(0).name());
    }

    @Test
    void testDeleteFolder_withValidPermissions_deletesSuccessfully() throws EntityNotFoundException {
        // Arrange
        String folderId = "folder-1";
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Docs", "team-1", null, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Team", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        
        when(folderRepository.findByParentFolderId(folderId)).thenReturn(List.of()); 

        // Act
        folderService.deleteFolder(folderId);

        // Assert
        verify(folderRepository, times(1)).deleteById(folderId);
    }

    @Test
    void testGetAllFolders_whenUserLacksPermissions_throwsForbidden() {
        // Arrange
        String teamId = "team-1";
        UserEntity strangerUser = new UserEntity("user-99", "Stranger", "stranger@test.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity(teamId, "Private Team", "Desc", "creator-id", List.of("user-1", "user-2"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(strangerUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.getAllFolders(teamId));
        verify(folderRepository, never()).findByTeamId(anyString());
    }

    @Test
    void testGetSubFolders_whenUserIsNotMember_throwsForbidden() {
        // Arrange
        String folderId = "folder-1";

        UserEntity strangerUser = new UserEntity("user-99", "Stranger", "stranger@test.ro", "pass", "user", Instant.now());
        FolderEntity parentFolder = new FolderEntity(folderId, "Parent", "team-1", null, "creator", Instant.now(), Instant.now());
        
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "creator", List.of("user-1", "user-2"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(strangerUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(parentFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.getSubFolders(folderId));
        
        verify(folderRepository, never()).findByParentFolderId(anyString());
    }

    @Test
    void testCreateFolder_withNonExistingParentFolder_throwsEntityNotFoundException() {
        // Arrange
        String invalidParentId = "invalid-parent-id";
        CreateFolderRequest request = new CreateFolderRequest("Subfolder", "team-1", invalidParentId);
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        
        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        
        when(folderRepository.findById(invalidParentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> folderService.createFolder(request));
        
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    













    @Test
    void testCreateFolder_withExistingSubfolderName_throwsBadRequest() throws EntityNotFoundException {
        String parentId = "parent-1";
        
        CreateFolderRequest request = new CreateFolderRequest("DuplicateSub", "team-1", parentId);
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        FolderEntity parentFolder = new FolderEntity(parentId, "Parent", "team-1", null, "user-1", Instant.now(), Instant.now());
        
        FolderEntity existingSubfolder = new FolderEntity("sub-1", "DuplicateSub", "team-1", parentId, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parentFolder));
        when(folderRepository.findByParentFolderId(parentId)).thenReturn(List.of(existingSubfolder));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.createFolder(request));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void testUpdateFolderName_subfolderWithExistingName_throwsBadRequest() throws EntityNotFoundException {
       
        String folderId = "sub-2";
        String newName = "DuplicateName";
        String parentId = "parent-1";
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Old Name", "team-1", parentId, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());
        FolderEntity siblingFolder = new FolderEntity("sub-1", "DuplicateName", "team-1", parentId, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        
        when(folderRepository.findByParentFolderId(parentId)).thenReturn(List.of(siblingFolder, existingFolder));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.updateFolderName(folderId, newName));
    }

    @Test
    void testUpdateFolderName_validSubfolder_triggersRecursiveParentUpdate() throws EntityNotFoundException {
    
        String folderId = "sub-1";
        String parentId = "parent-1";
        String newName = "New Sub Name";
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "a@t.ro", "p", "admin", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Old Name", "team-1", parentId, "user-1", Instant.now(), Instant.now());
        FolderEntity parentFolder = new FolderEntity(parentId, "Parent Name", "team-1", null, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Team", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByParentFolderId(parentId)).thenReturn(List.of(existingFolder)); 
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parentFolder));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        FolderResponse result = folderService.updateFolderName(folderId, newName);

        // Assert
        assertEquals(newName, result.name());
        verify(folderRepository, atLeast(2)).save(any(FolderEntity.class)); 
    }

    @Test
    void testDeleteFolder_withSubfolders_deletesRecursively() throws EntityNotFoundException {
        String parentId = "parent-1";
        String childId = "child-1";
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "a@t.ro", "p", "admin", Instant.now());
        FolderEntity parentFolder = new FolderEntity(parentId, "Parent", "team-1", null, "user-1", Instant.now(), Instant.now());
        FolderEntity childFolder = new FolderEntity(childId, "Child", "team-1", parentId, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Team", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parentFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        
        when(folderRepository.findByParentFolderId(parentId)).thenReturn(List.of(childFolder));
        when(folderRepository.findByParentFolderId(childId)).thenReturn(List.of());

        // Act
        folderService.deleteFolder(parentId);

        // Assert
        verify(folderRepository, times(1)).deleteById(childId); 
        verify(folderRepository, times(1)).deleteById(parentId);
    }





    @Test
    void testGetAllFolders_whenUserIsTeamOwner_returnsFolders() throws EntityNotFoundException {
        // Arrange
        String teamId = "team-1";
        UserEntity ownerUser = new UserEntity("user-1", "Owner", "owner@test.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity(teamId, "Team", "Desc", "user-1", List.of(), Instant.now());
        FolderEntity folder = new FolderEntity("folder-1", "Project", teamId, null, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(ownerUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId(teamId)).thenReturn(List.of(folder));

        // Act
        List<FolderResponse> result = folderService.getAllFolders(teamId);

        // Assert
        assertEquals(1, result.size());
        verify(folderRepository, times(1)).findByTeamId(teamId);
    }


    @Test
    void testUpdateFolderName_rootFolder_updatesSuccessfully() throws EntityNotFoundException {
        // Arrange
        String folderId = "root-1";
        String newName = "New Root Name";
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        
        
        FolderEntity existingFolder = new FolderEntity(folderId, "Old Root", "team-1", null, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        
        when(folderRepository.findByTeamId("team-1")).thenReturn(List.of(existingFolder));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        FolderResponse result = folderService.updateFolderName(folderId, newName);

        // Assert
        assertEquals(newName, result.name());
    }


    @Test
    void testUpdateFolderName_brokenParentChain_breaksLoopGracefully() throws EntityNotFoundException {
        // Arrange
        String folderId = "sub-1";
        String ghostParentId = "ghost-parent";
        
        UserEntity adminUser = new UserEntity("user-1", "Admin", "admin@test.ro", "pass", "admin", Instant.now());
        FolderEntity existingFolder = new FolderEntity(folderId, "Old Name", "team-1", ghostParentId, "user-1", Instant.now(), Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Backend", "Desc", "user-1", List.of("user-1"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(adminUser);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(existingFolder));
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByParentFolderId(ghostParentId)).thenReturn(List.of(existingFolder));
        
        when(folderRepository.findById(ghostParentId)).thenReturn(Optional.empty());
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        folderService.updateFolderName(folderId, "New Name");
        
        verify(folderRepository, times(1)).save(any(FolderEntity.class));
    }

    @Test
    void testGetSubFolders_whenTeamIsMissing_returnsForbidden() {
        // Arrange
        String folderId = "folder-1";
        UserEntity user = new UserEntity("user-1", "User", "user@test.ro", "pass", "user", Instant.now());
        FolderEntity parentFolder = new FolderEntity(folderId, "Parent", "ghost-team", null, "user-1", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(user);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(parentFolder));
        
        when(teamRepository.findById("ghost-team")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.getSubFolders(folderId));
    }





    @Test
    void testGetAllFolders_withNullRoleAndNullMembers_throwsForbidden() {
        String teamId = "team-1";
        UserEntity userWithNullRole = new UserEntity("user-1", "Name", "e@t.ro", "pass", null, Instant.now());
        TeamEntity teamWithNullMembers = new TeamEntity(teamId, "Team", "Desc", "other", null, Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(userWithNullRole);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamWithNullMembers));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.getAllFolders(teamId));
    }


    @Test
    void testCreateFolder_whenUserLacksPermissions_throwsForbidden() {
        CreateFolderRequest request = new CreateFolderRequest("NewFolder", "team-1", null);
        UserEntity stranger = new UserEntity("stranger", "Name", "e@t.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Team", "Desc", "owner", List.of("other-user"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(stranger);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> folderService.createFolder(request));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }
}