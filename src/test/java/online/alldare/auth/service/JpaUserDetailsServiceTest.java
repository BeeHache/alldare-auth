package online.alldare.auth.service;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.common.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaUserDetailsServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private JpaUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_UserExists_ReturnsUserDetails() {
        String login = "testuser";
        Role role = Role.builder().id(3).name("USER").build();
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .login(login)
                .passwordHash("hashedpassword")
                .roles(Set.of(role))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findByLogin(login)).thenReturn(Optional.of(account));

        UserDetails userDetails = userDetailsService.loadUserByUsername(login);

        assertThat(userDetails.getUsername()).isEqualTo(login);
        assertThat(userDetails.getPassword()).isEqualTo("hashedpassword");
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
    }

    @Test
    void loadUserByUsername_MultipleRoles_ReturnsUserDetailsWithAllAuthorities() {
        String login = "multiuser";
        Role role1 = Role.builder().id(2).name("MOD").build();
        Role role2 = Role.builder().id(3).name("USER").build();
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .login(login)
                .passwordHash("hashedpassword")
                .roles(Set.of(role1, role2))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findByLogin(login)).thenReturn(Optional.of(account));

        UserDetails userDetails = userDetailsService.loadUserByUsername(login);

        assertThat(userDetails.getAuthorities()).hasSize(2);
        assertThat(userDetails.getAuthorities())
                .extracting(auth -> auth.getAuthority())
                .containsExactlyInAnyOrder("ROLE_MOD", "ROLE_USER");
    }

    @Test
    void loadUserByUsername_UserIsBanned_ReturnsLockedUserDetails() {
        String login = "banneduser";
        Role role = Role.builder().id(3).name("USER").build();
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .login(login)
                .passwordHash("hashedpassword")
                .roles(Set.of(role))
                .status(AccountStatus.BANNED)
                .build();

        when(accountRepository.findByLogin(login)).thenReturn(Optional.of(account));

        UserDetails userDetails = userDetailsService.loadUserByUsername(login);

        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsException() {
        String login = "missinguser";
        when(accountRepository.findByLogin(login)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(login))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_AdminNotFoundInDb_ThrowsException() {
        String login = "admin";
        when(accountRepository.findByLogin(login)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(login))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
