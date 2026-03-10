package ro.unibuc.prodeng.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.http.HttpServletRequest;
import ro.unibuc.prodeng.response.FolderResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.FolderEntity;
import ro.unibuc.prodeng.repository.FolderRepository;


@Service
public class FolderService {
     @Autowired
    private FolderRepository folderRepository;

    // @Autowired
    // private TeamRepository teamRepository;---to do

    @Autowired
    private HttpServletRequest request; 
    private final String SECRET = "cheia-lui-mihaita-de-la-332";

    public List<FolderResponse> getAllFolders(String teamId) throws EntityNotFoundException {
        String currentUserEmail = getEmailFromToken();
        
        // //Check there is the team -- to do
        // TeamEntity team = teamRepository.findById(teamId)
        //         .orElseThrow(() -> new EntityNotFoundException("Team doesn't exist"));

        // //Check if user is member
        // boolean isMember = team.getMembers().contains(currentUserEmail);
        
        // if (!isMember) {
        //     throw new AccessDeniedException("You aren't a member of the team");
        // }


        return folderRepository.findByTeamId(teamId).stream()
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

    private String getEmailFromToken() {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token doesn't exist");
        }

        try {
            String token = authHeader.substring(7);
            
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            
            return jwt.getSubject();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalide or expired token!");
        }
    }
}
