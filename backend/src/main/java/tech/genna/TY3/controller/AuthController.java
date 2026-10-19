package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tech.genna.TY3.api.AuthApi;
import tech.genna.TY3.api.model.AuthResponse;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.api.model.RegisterRequest;
import tech.genna.TY3.service.auth.AuthService;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<AuthResponse> register(RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
