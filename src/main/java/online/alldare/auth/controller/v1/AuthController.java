package online.alldare.auth.controller.v1;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.dto.LoginRequest;
import online.alldare.auth.dto.LoginResponse;
import online.alldare.auth.repository.AccountRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import online.alldare.auth.messaging.MessagePublisher;
import online.alldare.common.event.AccountLoginEvent;
import jakarta.servlet.http.HttpServletRequest;
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
    private final MessagePublisher messagePublisher;

    @Value("${alldare.auth.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    public AuthController(AccountRepository accountRepository, 
                          AuthenticationManager authenticationManager, 
                          JwtEncoder jwtEncoder,
                          MessagePublisher messagePublisher) {
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.messagePublisher = messagePublisher;
    }

    /**
     * Authenticates a user with login and password and returns a JWT token.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getLogin(), loginRequest.getPassword())
        );

        // Standard users should use Social Login
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Local login disabled for standard users. Please use Social Login.");
        }

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
        
        // Handle in-memory admin
        if ("admin".equals(authentication.getName())) {
            claimsBuilder.claim("userId", "00000000-0000-0000-0000-000000000001");
        }

        JwtClaimsSet claims = claimsBuilder.build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        publishLoginEvent(authentication.getName(), request, "LOCAL");

        return new LoginResponse(token);
    }

    private void publishLoginEvent(String login, HttpServletRequest request, String method) {
        try {
            accountRepository.findByLogin(login).ifPresent(account -> {
                AccountLoginEvent event = AccountLoginEvent.builder()
                        .accountId(account.getId())
                        .login(login)
                        .loginTime(Instant.now())
                        .ipAddress(getClientIp(request))
                        .userAgent(request.getHeader("User-Agent"))
                        .loginMethod(method)
                        .build();
                
                messagePublisher.publish("stream:logins", event);
                log.info("Published AccountLoginEvent for user: {}", login);
            });
        } catch (Exception e) {
            log.error("Failed to publish login event for user: {}", login, e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
                .orElseGet(() -> {
                    // Check if it's the in-memory admin
                    if ("admin".equals(login)) {
                        Account adminAccount = new Account();
                        adminAccount.setId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
                        adminAccount.setLogin("admin");
                        adminAccount.setAccountType(online.alldare.common.enums.AccountType.ADMIN);
                        adminAccount.setStatus(online.alldare.common.enums.AccountStatus.ACTIVE);
                        adminAccount.setRoles(java.util.Set.of(
                            online.alldare.auth.domain.entity.Role.builder().name("ADMIN").build()
                        ));
                        return adminAccount;
                    }
                    log.error("Account NOT found for login: {}", login);
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
                });
    }
}
