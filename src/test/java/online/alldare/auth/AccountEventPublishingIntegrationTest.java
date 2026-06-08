package online.alldare.auth;

import tools.jackson.databind.ObjectMapper;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;
import online.alldare.common.event.AccountCreatedEvent;
import online.alldare.common.event.AccountLoginEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
public class AccountEventPublishingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldPublishAccountCreatedEventOnRegistration() throws Exception {
        // Given
        String login = "event-test-user";
        String json = """
                {
                  "login": "%s",
                  "password": "password123"
                }
                """.formatted(login);

        // When
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // Then
        List<ObjectRecord<String, String>> records = redisTemplate.opsForStream()
                .read(String.class, StreamOffset.create("stream:accounts", ReadOffset.from("0-0")));

        assertThat(records).isNotEmpty();
        
        boolean found = false;
        for (ObjectRecord<String, String> record : records) {
            String jsonPayload = record.getValue();
            AccountCreatedEvent event = objectMapper.readValue(jsonPayload, AccountCreatedEvent.class);
            
            if (login.equals(event.login())) {
                found = true;
                assertThat(event.roles()).contains("USER");
                break;
            }
        }
        
        assertThat(found).as("AccountCreatedEvent for %s should be in Redis Stream", login).isTrue();
    }

    @Test
    void shouldPublishAccountLoginEventOnSuccessfulLogin() throws Exception {
        // Given - Create an ADMIN user manually to bypass Social Login restriction
        String login = "admin-login-test";
        String password = "password123";
        
        Account admin = Account.builder()
                .id(UUID.randomUUID())
                .login(login)
                .passwordHash(passwordEncoder.encode(password))
                .accountType(AccountType.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        
        accountRepository.saveAndFlush(admin);
        assertThat(admin.getLastLogin()).isNull();

        // When - Perform login
        String loginJson = """
                {
                  "login": "%s",
                  "password": "%s"
                }
                """.formatted(login, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk());

        // Then - Check Redis Stream
        List<ObjectRecord<String, String>> records = redisTemplate.opsForStream()
                .read(String.class, StreamOffset.create("stream:logins", ReadOffset.from("0-0")));

        assertThat(records).isNotEmpty();
        
        boolean found = false;
        for (ObjectRecord<String, String> record : records) {
            String jsonPayload = record.getValue();
            AccountLoginEvent event = objectMapper.readValue(jsonPayload, AccountLoginEvent.class);
            
            if (login.equals(event.login())) {
                found = true;
                assertThat(event.loginMethod()).isEqualTo("LOCAL");
                assertThat(event.accountId()).isEqualTo(admin.getId());
                assertThat(event.loginTime()).isNotNull();
                break;
            }
        }
        
        assertThat(found).as("AccountLoginEvent for %s should be in Redis Stream", login).isTrue();

        // Then - Verify database update (asynchronous)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Account updatedAccount = accountRepository.findById(admin.getId()).orElseThrow();
            assertThat(updatedAccount.getLastLogin()).isNotNull();
        });
    }
}
