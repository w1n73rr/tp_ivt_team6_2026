package tech.genna.TY3.service.auth;

import tech.genna.TY3.api.model.AuthResponse;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.api.model.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    

}
