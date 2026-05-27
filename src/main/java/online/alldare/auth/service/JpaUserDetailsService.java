package online.alldare.auth.service;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.common.enums.AccountStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

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
