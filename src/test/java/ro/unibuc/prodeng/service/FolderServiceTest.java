package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.model.TeamEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.FolderRepository;
import ro.unibuc.prodeng.repository.TeamRepository;
import ro.unibuc.prodeng.response.FolderResponse;

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
    void testGetAllFolders_adminCallerProvided_returnsFolders() {
        // Enrollment&access: RBAC -> Admin callers bypass team membership restrictions when reading folders.
        UserEntity admin = new UserEntity("admin-id", "Admin", "admin@uni.ro", "pass", "admin", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "owner-id", List.of("member-id"), Instant.now());
        FolderEntity folder = new FolderEntity("folder-1", "Docs", "team-1", null, "owner-id", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(admin);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId("team-1")).thenReturn(Optional.of(folder));

        List<FolderResponse> result = folderService.getAllFolders("team-1");

        assertEquals(1, result.size());
        assertEquals("folder-1", result.get(0).id());
    }

    @Test
    void testGetAllFolders_teamMemberProvided_returnsFolders() {
        // Enrollment&access: RBAC -> Allow enrolled members to read folders from their own team.
        UserEntity member = new UserEntity("member-id", "Member", "member@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "owner-id", List.of("member-id"), Instant.now());
        FolderEntity folder = new FolderEntity("folder-1", "Docs", "team-1", null, "member-id", Instant.now(), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(member);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));
        when(folderRepository.findByTeamId("team-1")).thenReturn(Optional.of(folder));

        List<FolderResponse> result = folderService.getAllFolders("team-1");

        assertEquals(1, result.size());
        assertEquals("folder-1", result.get(0).id());
        assertEquals("Docs", result.get(0).name());
    }

    @Test
    void testGetAllFolders_nonMemberProvided_throwsForbidden() {
        // Enrollment&access: RBAC -> Deny folder access for users outside the requested team.
        UserEntity outsider = new UserEntity("user-id", "Outsider", "outsider@uni.ro", "pass", "user", Instant.now());
        TeamEntity team = new TeamEntity("team-1", "Engineering", "Main", "owner-id", List.of("member-id"), Instant.now());

        when(authContextService.getCurrentUserFromToken()).thenReturn(outsider);
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> folderService.getAllFolders("team-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}