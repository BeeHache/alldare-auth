package online.alldare.auth.service;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
import online.alldare.auth.domain.entity.User;
import online.alldare.auth.dto.RegisterRequest;
import online.alldare.auth.dto.RegisterResponse;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.auth.repository.RoleRepository;
import online.alldare.auth.repository.UserRepository;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

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

        return RegisterResponse.builder()
                .accountId(accountId)
                .userId(userId)
                .login(account.getLogin())
                .build();
    }
}
