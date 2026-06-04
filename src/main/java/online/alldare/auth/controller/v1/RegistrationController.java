package online.alldare.auth.controller.v1;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.alldare.auth.dto.RegisterRequest;
import online.alldare.auth.dto.RegisterResponse;
import online.alldare.auth.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final AccountService accountService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return accountService.registerUser(request);
    }
}
