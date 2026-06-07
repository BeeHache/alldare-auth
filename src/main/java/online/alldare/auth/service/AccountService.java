package online.alldare.auth.service;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
import online.alldare.auth.domain.entity.User;
import online.alldare.auth.dto.AccountDTO;
import online.alldare.auth.dto.RegisterRequest;
import online.alldare.auth.dto.RegisterResponse;
import online.alldare.auth.messaging.MessagePublisher;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.auth.repository.RoleRepository;
import online.alldare.auth.repository.UserRepository;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;
import online.alldare.common.event.AccountCreatedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessagePublisher messagePublisher;

    private static final String ACCOUNTS_STREAM = "stream:accounts";

    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        if (accountRepository.findByLogin(request.getLogin()).isPresent()) {
            throw new RuntimeException("Login already exists: " + request.getLogin());
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default USER role not found"));

        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .login(request.getLogin())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(userRole))
                .accountType(AccountType.USER)
                .build();

        accountRepository.save(account);

        UUID userId = UUID.randomUUID();
        User userProfile = User.builder()
                .id(userId)
                .accountId(accountId)
                .build();

        userRepository.save(userProfile);

        publishAccountCreatedEvent(account);

        return RegisterResponse.builder()
                .accountId(accountId)
                .userId(userId)
                .login(account.getLogin())
                .build();
    }

    @Transactional
    public Account provisionOAuth2User(OAuth2User oauth2User, String provider) {
        String providerId;
        String email;

        if (oauth2User instanceof OidcUser oidcUser) {
            providerId = oidcUser.getSubject();
            email = oidcUser.getEmail();
        } else {
            // Standard OAuth2 (e.g. GitHub)
            Object id = oauth2User.getAttribute("id");
            providerId = id != null ? id.toString() : oauth2User.getName();
            email = oauth2User.getAttribute("email");
        }

        return accountRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    String login = email != null ? email : provider.toLowerCase() + "_" + providerId;

                    return accountRepository.findByLogin(login)
                            .map(account -> {
                                account.setProvider(provider);
                                account.setProviderId(providerId);
                                return accountRepository.save(account);
                            })
                            .orElseGet(() -> {
                                Role userRole = roleRepository.findByName("USER")
                                        .orElseThrow(() -> new RuntimeException("Default USER role not found"));

                                UUID accountId = UUID.randomUUID();
                                Account account = Account.builder()
                                        .id(accountId)
                                        .login(login)
                                        .status(AccountStatus.ACTIVE)
                                        .roles(Set.of(userRole))
                                        .accountType(AccountType.USER)
                                        .provider(provider)
                                        .providerId(providerId)
                                        .build();

                                accountRepository.save(account);

                                UUID userId = UUID.randomUUID();
                                User userProfile = User.builder()
                                        .id(userId)
                                        .accountId(accountId)
                                        .build();

                                userRepository.save(userProfile);

                                publishAccountCreatedEvent(account, oauth2User);

                                return account;
                            });
                });
    }

    @Transactional(readOnly = true)
    public Page<AccountDTO> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    @Transactional
    public AccountDTO updateAccountStatus(UUID id, AccountStatus status) {
        if (UUID.fromString("00000000-0000-0000-0000-000000000001").equals(id)) {
            throw new RuntimeException("Cannot modify status of the default admin account");
        }
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        account.setStatus(status);
        return convertToDTO(accountRepository.save(account));
    }

    private AccountDTO convertToDTO(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .login(account.getLogin())
                .provider(account.getProvider())
                .status(account.getStatus())
                .accountType(account.getAccountType())
                .roles(account.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(account.getCreatedAt())
                .lastLogin(account.getLastLogin())
                .build();
    }

    private void publishAccountCreatedEvent(Account account) {
        publishAccountCreatedEvent(account, null);
    }

    private void publishAccountCreatedEvent(Account account, OAuth2User oauth2User) {
        String displayName = null;
        String avatarUrl = null;

        if (oauth2User != null) {
            displayName = oauth2User.getAttribute("name");
            avatarUrl = oauth2User.getAttribute("avatar_url");
            if (avatarUrl == null) {
                avatarUrl = oauth2User.getAttribute("picture"); // Google
            }
        }

        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountId(account.getId())
                .login(account.getLogin())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .roles(account.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();

        messagePublisher.publish(ACCOUNTS_STREAM, event);
    }
}
