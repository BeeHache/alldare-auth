package online.alldare.auth;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.domain.entity.Role;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AuthApplicationTests {

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
    void contextLoads() {
    }

    @Test
    void shouldRegisterUser() throws Exception {
        String json = """
                {
                  "login": "newuser",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("newuser"))
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.userId").exists());

        assertThat(accountRepository.findByLogin("newuser")).isPresent();
    }

    @Test
    void shouldCreateAndRetrieveAccountWithRoles() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        UUID id = UUID.randomUUID();
        Account account = Account.builder()
                .id(id)
                .login("testuser")
                .passwordHash("hashedpassword")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(userRole))
                .accountType(AccountType.USER)
                .build();

        accountRepository.saveAndFlush(account);

        Account savedAccount = accountRepository.findById(id).orElseThrow();
        assertThat(savedAccount.getLogin()).isEqualTo("testuser");
        assertThat(savedAccount.getRoles()).hasSize(1);
        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistSubrole() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        
        Role subRole = Role.builder()
                .id(100)
                .name("SUPER_ADMIN")
                .parentRole(adminRole)
                .build();
        
        roleRepository.save(subRole);

        Role savedSubRole = roleRepository.findById(100).orElseThrow();
        assertThat(savedSubRole.getName()).isEqualTo("SUPER_ADMIN");
        assertThat(savedSubRole.getParentRole()).isNotNull();
        assertThat(savedSubRole.getParentRole().getName()).isEqualTo("ADMIN");
    }

    @Test
    void shouldPersistMultipleRolesForAccount() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        Role modRole = roleRepository.findByName("MOD").orElseThrow();
        
        UUID id = UUID.randomUUID();
        Account account = Account.builder()
                .id(id)
                .login("multi_role_user")
                .passwordHash("hashedpassword")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(userRole, modRole))
                .accountType(AccountType.USER)
                .build();

        accountRepository.saveAndFlush(account);

        Account savedAccount = accountRepository.findById(id).orElseThrow();
        assertThat(savedAccount.getRoles()).hasSize(2);
        assertThat(savedAccount.getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("USER", "MOD");
    }
}
