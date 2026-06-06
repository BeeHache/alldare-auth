package online.alldare.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${alldare.admin.default.login:admin}")
    private String defaultAdminLogin;

    @Value("${alldare.admin.default.password:}")
    private String defaultAdminPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationEvent() {
        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            log.warn("Default admin password is not set. Admin account password will not be updated.");
            return;
        }

        accountRepository.findByLogin(defaultAdminLogin).ifPresentOrElse(
                adminAccount -> {
                    adminAccount.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
                    accountRepository.save(adminAccount);
                    log.info("Default admin password has been updated from configuration.");
                },
                () -> log.warn("Admin account with login '{}' not found in database.", defaultAdminLogin)
        );
    }
}
