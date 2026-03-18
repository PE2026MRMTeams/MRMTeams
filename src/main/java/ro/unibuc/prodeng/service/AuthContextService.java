package ro.unibuc.prodeng.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.http.HttpServletRequest;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;

@Service
public class AuthContextService {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private UserRepository userRepository;

    private static final String SECRET = "cheia-lui-mihaita-de-la-332";

    // Enrollment&access: RBAC -> Resolve the authenticated user email from the Bearer token.
    public String getEmailFromToken() {

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

    // Enrollment&access: RBAC -> Resolve the current user entity once and reuse it across services.
    public UserEntity getCurrentUserFromToken() {
        String email = getEmailFromToken();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email: " + email));
    }
}