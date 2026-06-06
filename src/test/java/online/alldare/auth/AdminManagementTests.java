package online.alldare.auth;

import online.alldare.auth.repository.AccountRepository;
import online.alldare.auth.repository.RoleRepository;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "DEFAULT_ADMIN_PASSWORD=admin-test-pass"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AdminManagementTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSeedAdminAccountOnStartupAndAllowLogin() throws Exception {
        String loginJson = """
                {
                  "login": "admin",
                  "password": "admin-test-pass"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowModLoginAndNotSetSessionCookie() throws Exception {
        // Create a MOD account
        online.alldare.auth.domain.entity.Role modRole = roleRepository.findByName("MOD").orElseThrow();
        java.util.UUID accountId = java.util.UUID.randomUUID();
        online.alldare.auth.domain.entity.Account modAccount = online.alldare.auth.domain.entity.Account.builder()
                .id(accountId)
                .login("testmod")
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("modpass"))
                .status(online.alldare.common.enums.AccountStatus.ACTIVE)
                .roles(java.util.Set.of(modRole))
                .accountType(online.alldare.common.enums.AccountType.ADMIN) // MODs are considered ADMIN account type for local login
                .build();
        accountRepository.saveAndFlush(modAccount);

        String loginJson = """
                {
                  "login": "testmod",
                  "password": "modpass"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldFailNormalUserLocalLogin() throws Exception {
        // Register a standard user
        String registerJson = """
                {
                  "login": "normaluser",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // Then, login attempt should fail with 401 and specific message
        String loginJson = """
                {
                  "login": "normaluser",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Local login disabled for standard users. Please use Social Login."))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldPreventStatusUpdateForDefaultAdmin() throws Exception {
        String updateStatusJson = """
                {
                  "status": "BANNED"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/users/00000000-0000-0000-0000-000000000001/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson))
                .andExpect(status().isInternalServerError()); // RuntimeException results in 500 by default
    }
}
