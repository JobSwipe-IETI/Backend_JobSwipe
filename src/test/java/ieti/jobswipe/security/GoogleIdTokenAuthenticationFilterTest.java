package ieti.jobswipe.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.context.SecurityContextHolder;

class GoogleIdTokenAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsMissing() throws Exception {
        IdentityTokenVerifier identityTokenVerifier = mock(IdentityTokenVerifier.class);
        UserProvisioningService userProvisioningService = mock(UserProvisioningService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        GoogleIdTokenAuthenticationFilter filter = new GoogleIdTokenAuthenticationFilter(
                identityTokenVerifier,
                userProvisioningService,
                authenticationEntryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(identityTokenVerifier, never()).verify(any());
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer() throws Exception {
        IdentityTokenVerifier identityTokenVerifier = mock(IdentityTokenVerifier.class);
        UserProvisioningService userProvisioningService = mock(UserProvisioningService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        GoogleIdTokenAuthenticationFilter filter = new GoogleIdTokenAuthenticationFilter(
                identityTokenVerifier,
                userProvisioningService,
                authenticationEntryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(identityTokenVerifier, never()).verify(any());
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectBlankBearerToken() throws Exception {
        IdentityTokenVerifier identityTokenVerifier = mock(IdentityTokenVerifier.class);
        UserProvisioningService userProvisioningService = mock(UserProvisioningService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        GoogleIdTokenAuthenticationFilter filter = new GoogleIdTokenAuthenticationFilter(
                identityTokenVerifier,
                userProvisioningService,
                authenticationEntryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(authenticationEntryPoint, times(1)).commence(any(), any(), any());
        verify(identityTokenVerifier, never()).verify(any());
    }

    @Test
    void shouldAuthenticateUserAndContinueFilterChainWhenTokenIsValid() throws Exception {
        IdentityTokenVerifier identityTokenVerifier = mock(IdentityTokenVerifier.class);
        UserProvisioningService userProvisioningService = mock(UserProvisioningService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        GoogleIdTokenAuthenticationFilter filter = new GoogleIdTokenAuthenticationFilter(
                identityTokenVerifier,
                userProvisioningService,
                authenticationEntryPoint);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("google-123", "john@example.com", "John", "https://avatar");

        when(identityTokenVerifier.verify("valid-token")).thenReturn(authenticatedUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(authenticatedUser, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(identityTokenVerifier, times(1)).verify("valid-token");
        verify(userProvisioningService, times(1)).ensureUserExists(authenticatedUser);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void shouldClearContextAndInvokeEntryPointWhenTokenIsInvalid() throws Exception {
        IdentityTokenVerifier identityTokenVerifier = mock(IdentityTokenVerifier.class);
        UserProvisioningService userProvisioningService = mock(UserProvisioningService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        GoogleIdTokenAuthenticationFilter filter = new GoogleIdTokenAuthenticationFilter(
                identityTokenVerifier,
                userProvisioningService,
                authenticationEntryPoint);

        when(identityTokenVerifier.verify("bad-token"))
                .thenThrow(new InvalidIdentityTokenException("Invalid Google identity token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticationEntryPoint, times(1)).commence(any(), any(), any());
        verify(userProvisioningService, never()).ensureUserExists(any());
    }
}

