package tech.genna.TY3.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import tech.genna.TY3.security.filter.JwtAuthFilter;
import tech.genna.TY3.service.jwt.JwtService;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void deletedUserTokenDoesNotThrowOrAuthenticate() throws Exception {
        var jwt=mock(JwtService.class); var users=mock(UserDetailsService.class);
        when(jwt.extractUsername("private-token")).thenReturn("deleted");
        when(users.loadUserByUsername("deleted")).thenThrow(new UsernameNotFoundException("deleted"));
        var request=new MockHttpServletRequest("PUT","/api/attempts/private-id/answers/question");
        request.addHeader("Authorization","Bearer private-token");
        var response=new MockHttpServletResponse(); var chain=new MockFilterChain();
        new JwtAuthFilter(jwt,users).doFilter(request,response,chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("no-store",response.getHeader("Cache-Control"));
        assertNotNull(chain.getRequest());
    }
    @Test void rejectedTokenNeverLoadsUser() throws Exception {
        var jwt=mock(JwtService.class);var users=mock(UserDetailsService.class);
        when(jwt.extractUsername(any())).thenThrow(new IllegalArgumentException("private-token"));
        var request=new MockHttpServletRequest("GET","/api/quizzes");
        request.addHeader("Authorization","Bearer private-token");
        new JwtAuthFilter(jwt,users).doFilter(request,new MockHttpServletResponse(),new MockFilterChain());
        verifyNoInteractions(users);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
