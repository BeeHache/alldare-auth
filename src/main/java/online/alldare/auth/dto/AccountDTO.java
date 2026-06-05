package online.alldare.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.alldare.common.enums.AccountStatus;
import online.alldare.common.enums.AccountType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private UUID id;
    private String login;
    private String provider;
    private AccountStatus status;
    private AccountType accountType;
    private Set<String> roles;
    private Instant createdAt;
    private Instant lastLogin;
}
