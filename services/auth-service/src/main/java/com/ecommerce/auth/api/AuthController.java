package com.ecommerce.auth.api;

import com.ecommerce.auth.api.AuthDtos.LoginRequest;
import com.ecommerce.auth.api.AuthDtos.RegisterRequest;
import com.ecommerce.auth.api.AuthDtos.TokenResponse;
import com.ecommerce.auth.api.AuthDtos.UserResponse;
import com.ecommerce.auth.application.AuthService;
import com.ecommerce.auth.domain.UserAccountRepository;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountRepository users;

    public AuthController(AuthService authService, UserAccountRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        var user = authService.register(request.email(), request.password(), request.fullName(), List.of("CUSTOMER"));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoles());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var token = authService.login(request.email(), request.password());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }

    @GetMapping("/me")
    UserResponse me() {
        var id = CurrentUser.id();
        var user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoles());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid e-mail or password.");
    }
}
