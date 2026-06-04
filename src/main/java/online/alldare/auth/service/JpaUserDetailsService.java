package online.alldare.auth.service;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${alldare.admin.default.login:admin}")
    private String defaultAdminLogin;

    @Value("${alldare.admin.default.password:}")
    private String defaultAdminPassword;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (defaultAdminLogin.equals(username) && defaultAdminPassword != null && !defaultAdminPassword.isEmpty()) {
            return User.builder()
                    .username(defaultAdminLogin)
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .roles("ADMIN")
                    .build();
        }

        Account account = accountRepository.findByLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (AccountType.USER.equals(account.getAccountType())) {
            throw new org.springframework.security.authentication.DisabledException("Local login disabled for standard users. Please use Social Login.");
        }

        String[] roles = account.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new);

        return User.builder()
                .username(account.getLogin())
                .password(account.getPasswordHash())
                .roles(roles)
                .accountExpired(false)
                .accountLocked(AccountStatus.BANNED.equals(account.getStatus()))
                .credentialsExpired(false)
                .disabled(AccountStatus.PENDING.equals(account.getStatus()))
                .build();
    }
}
