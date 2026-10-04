package tech.genna.TY3.service.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.UUID;

public interface CustomUserDetailsService extends UserDetailsService {

    UserDetails loadUserByUsername(String username);
    UserDetails loadUserById(UUID id);
    
}