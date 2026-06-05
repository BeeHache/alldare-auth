package online.alldare.auth.controller.v1;

import lombok.RequiredArgsConstructor;
import online.alldare.auth.dto.AccountDTO;
import online.alldare.auth.dto.UpdateStatusRequest;
import online.alldare.auth.service.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
public class AdminController {

    private final AccountService accountService;

    @GetMapping("/users")
    public Page<AccountDTO> getUsers(Pageable pageable) {
        return accountService.getAllAccounts(pageable);
    }

    @PatchMapping("/users/{id}/status")
    public AccountDTO updateUserStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        return accountService.updateAccountStatus(id, request.getStatus());
    }
}
