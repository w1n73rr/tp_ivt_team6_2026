package tech.genna.TY3.service.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.entity.User;
import tech.genna.TY3.repository.UserRepository;
import tech.genna.TY3.service.jwt.JwtService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void emailLoginAuthenticatesCanonicalUsername() {
        User user = User.builder().username("student").email("student@example.org").build();
        when(userRepository.findByUsername("student@example.org")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.org")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt");

        var response = authService.login(new LoginRequest("student@example.org", "secret123"));

        var authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertEquals("student", authentication.getValue().getName());
        assertEquals("secret123", authentication.getValue().getCredentials());
        assertEquals("jwt", response.getToken());
    }

    @Test
    void unknownUserProducesGenericCredentialFailure() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("unknown", "secret123")));
    }
}
