package online.alldare.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.service.AccountService;
import online.alldare.auth.messaging.MessagePublisher;
import online.alldare.common.event.AccountLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final JwtEncoder jwtEncoder;
    private final AccountService accountService;
    private final MessagePublisher messagePublisher;

    @Value("${alldare.auth.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    public OAuth2AuthenticationSuccessHandler(JwtEncoder jwtEncoder, 
                                            AccountService accountService,
                                            MessagePublisher messagePublisher) {
        this.jwtEncoder = jwtEncoder;
        this.accountService = accountService;
        this.messagePublisher = messagePublisher;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            return "/auth/login?error=invalid_token";
        }

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        // Provision/Retrieve the internal account
        Account account = accountService.provisionOAuth2User(oauth2User, registrationId.toUpperCase());

        // Detect if the account was newly provisioned (null createdAt or within last 10 seconds)
        boolean isNewUser = account.getCreatedAt() == null || account.getCreatedAt().isAfter(Instant.now().minusSeconds(10));

        Instant now = Instant.now();
        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(account.getLogin()) // Use internal login (email) as subject
                .claim("userId", account.getId().toString())
                .claim("roles", scope.replace("ROLE_", ""));

        JwtClaimsSet claims = claimsBuilder.build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        publishLoginEvent(account, request, registrationId.toUpperCase());

        // Add cookie for local development convenience
        Cookie authCookie = new Cookie("auth_token", token);
        authCookie.setPath("/");
        authCookie.setHttpOnly(false); // Frontend needs to read it
        authCookie.setMaxAge(3600);
        response.addCookie(authCookie);

        return UriComponentsBuilder.fromUriString("https://localhost/auth/callback")
                .queryParam("token", token)
                .queryParam("newRegistration", isNewUser)
                .build().toUriString();
    }

    private void publishLoginEvent(Account account, HttpServletRequest request, String method) {
        try {
            AccountLoginEvent event = AccountLoginEvent.builder()
                    .accountId(account.getId())
                    .login(account.getLogin())
                    .loginTime(Instant.now())
                    .ipAddress(getClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .loginMethod(method)
                    .build();
            
            messagePublisher.publish("stream:logins", event);
            log.info("Published AccountLoginEvent (OAuth2) for user: {}", account.getLogin());
        } catch (Exception e) {
            log.error("Failed to publish OAuth2 login event for user: {}", account.getLogin(), e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
