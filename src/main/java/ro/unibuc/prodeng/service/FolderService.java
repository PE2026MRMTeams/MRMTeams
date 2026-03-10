package ro.unibuc.prodeng.service;

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


@Service
public class FolderService {
     @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuthContextService authContextService;

    public List<FolderResponse> getAllFolders(String teamId) throws EntityNotFoundException {
        String requiredTeamId = Objects.requireNonNull(teamId, "Team id is required");

        // Enrollment&access: RBAC -> Resolve current caller through the shared auth context before membership checks.
        UserEntity currentUser = authContextService.getCurrentUserFromToken();

        // Enrollment&access: RBAC -> Load the team to validate enrollment membership before exposing folder data.
        TeamEntity team = teamRepository.findById(requiredTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team with id: " + requiredTeamId));

        // Enrollment&access: RBAC -> Grant access only for admin users or users enrolled in the requested team.
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

    private FolderResponse toResponse(FolderEntity folder) {
        return new FolderResponse(
                folder.id(),
                folder.name(),
                folder.createdAt(),
                folder.modifiedAt()
        );
    }

}
