package online.alldare.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.service.AccountService;
import online.alldare.auth.messaging.MessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OAuth2AuthenticationSuccessHandlerTest {

    private JwtEncoder jwtEncoder;
    private AccountService accountService;
    private MessagePublisher messagePublisher;
    private OAuth2AuthenticationSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        jwtEncoder = mock(JwtEncoder.class);
        accountService = mock(AccountService.class);
        messagePublisher = mock(MessagePublisher.class);
        successHandler = new OAuth2AuthenticationSuccessHandler(jwtEncoder, accountService, messagePublisher);
        org.springframework.test.util.ReflectionTestUtils.setField(successHandler, "issuerUri", "http://localhost:9000");
    }

    @Test
    void shouldAppendNewRegistrationTrueWhenUserIsNew() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        
        OAuth2User oauth2User = mock(OAuth2User.class);
        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Mockito.doReturn(authorities).when(authentication).getAuthorities();

        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .login("test@gmail.com")
                .createdAt(null) // brand new
                .build();
        when(accountService.provisionOAuth2User(any(), any())).thenReturn(account);

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("mock-token-123");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        String targetUrl = successHandler.determineTargetUrl(request, response, authentication);

        // Then
        assertThat(targetUrl).contains("token=mock-token-123");
        assertThat(targetUrl).contains("newRegistration=true");
    }

    @Test
    void shouldAppendNewRegistrationFalseWhenUserIsOld() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        
        OAuth2User oauth2User = mock(OAuth2User.class);
        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Mockito.doReturn(authorities).when(authentication).getAuthorities();

        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .login("test@gmail.com")
                .createdAt(Instant.now().minusSeconds(3600)) // created an hour ago
                .build();
        when(accountService.provisionOAuth2User(any(), any())).thenReturn(account);

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("mock-token-123");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // When
        String targetUrl = successHandler.determineTargetUrl(request, response, authentication);

        // Then
        assertThat(targetUrl).contains("token=mock-token-123");
        assertThat(targetUrl).contains("newRegistration=false");
    }
}
