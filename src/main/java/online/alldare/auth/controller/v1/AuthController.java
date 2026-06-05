package online.alldare.auth.controller.v1;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.dto.LoginRequest;
import online.alldare.auth.dto.LoginResponse;
import online.alldare.auth.repository.AccountRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
import org.springframework.security.core.GrantedAuthority;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountRepository accountRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    public AuthController(AccountRepository accountRepository, AuthenticationManager authenticationManager, JwtEncoder jwtEncoder) {
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getLogin(), loginRequest.getPassword())
        );

        Instant now = Instant.now();
        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost:9000") // Should match AUTH_ISSUER_URI
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(authentication.getName())
                .claim("roles", scope.replace("ROLE_", ""));

        accountRepository.findByLogin(authentication.getName())
                .ifPresent(account -> claimsBuilder.claim("userId", account.getId().toString()));
        
        // Handle in-memory admin
        if ("admin".equals(authentication.getName())) {
            claimsBuilder.claim("userId", "00000000-0000-0000-0000-000000000001");
        }

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        
        String login = authentication.getName();
        return accountRepository.findByLogin(login)
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
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
                });
    }
}
