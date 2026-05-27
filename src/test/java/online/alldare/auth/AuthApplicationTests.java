package online.alldare.auth;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateAndRetrieveAccount() {
        UUID id = UUID.randomUUID();
        Account account = Account.builder()
                .id(id)
                .login("testuser")
                .passwordHash("hashedpassword")
                .status("ACTIVE")
                .role("USER")
                .accountType("USER")
                .build();

        accountRepository.save(account);

        Account savedAccount = accountRepository.findById(id).orElseThrow();
        assertThat(savedAccount.getLogin()).isEqualTo("testuser");
        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();
    }
}
