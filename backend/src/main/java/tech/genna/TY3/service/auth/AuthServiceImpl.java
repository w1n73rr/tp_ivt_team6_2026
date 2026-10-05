package tech.genna.TY3.service.auth;

import tech.genna.TY3.dto.auth.AuthResponse;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.dto.auth.RegisterRequest;
import tech.genna.TY3.service.jwt.JwtService;
import tech.genna.TY3.model.Role;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Имя пользователя уже занято");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email уже зарегистрирован");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }
}
