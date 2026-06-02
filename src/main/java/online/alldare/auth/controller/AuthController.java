package online.alldare.auth.controller;

import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.auth.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountRepository accountRepository;

    public AuthController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the current authenticated user's account details.
     */
    @GetMapping("/me")
    public Account getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return accountRepository.findByLogin(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }
}
