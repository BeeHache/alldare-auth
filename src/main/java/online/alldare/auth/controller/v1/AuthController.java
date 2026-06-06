package online.alldare.auth.controller.v1;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.dto.LoginRequest;
import online.alldare.auth.dto.LoginResponse;
import online.alldare.auth.repository.AccountRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * REST controller for handling authentication and user profile requests.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AccountRepository accountRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    @Value("${alldare.auth.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    public AuthController(AccountRepository accountRepository, AuthenticationManager authenticationManager, JwtEncoder jwtEncoder) {
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Authenticates a user with login and password and returns a JWT token.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getLogin(), loginRequest.getPassword())
        );

        Instant now = Instant.now();
        String scope = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(authentication.getName())
                .claim("roles", scope.replace("ROLE_", ""));

        // Optionally add internal user ID to claims if available
        accountRepository.findByLogin(authentication.getName())
                .ifPresent(account -> claimsBuilder.claim("userId", account.getId().toString()));

        JwtClaimsSet claims = claimsBuilder.build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return new LoginResponse(token);
    }

    /**
     * Returns the current authenticated user's account details.
     */
    @GetMapping("/me")
    public Account getMe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("getMe called with unauthenticated request");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        
        String login = authentication.getName();
        log.info("Fetching profile for authenticated login: {}", login);
        
        return accountRepository.findByLogin(login)
                .map(account -> {
                    log.info("Account found for login: {}", login);
                    return account;
                })
                .orElseThrow(() -> {
                    log.error("Account NOT found for login: {}", login);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
                });
    }
}
