package online.alldare.auth.service;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.domain.entity.Account;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final AccountService accountService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        // Provision internal account based on OIDC user info
        Account internalAccount = accountService.provisionOAuth2User(oidcUser, registrationId.toUpperCase());
        
        // We can wrap or extend the user if we need to add custom authorities based on internalAccount
        return oidcUser;
    }
}
