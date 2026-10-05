package tech.genna.TY3.service.auth;

import tech.genna.TY3.dto.auth.AuthResponse;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    

}
