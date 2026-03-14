package ieti.JobSwipe.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GoogleIdTokenAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityTokenVerifier identityTokenVerifier;
    private final UserProvisioningService userProvisioningService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public GoogleIdTokenAuthenticationFilter(IdentityTokenVerifier identityTokenVerifier,
                                             UserProvisioningService userProvisioningService,
                                             AuthenticationEntryPoint authenticationEntryPoint) {
        this.identityTokenVerifier = identityTokenVerifier;
        this.userProvisioningService = userProvisioningService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            authenticationEntryPoint.commence(request, response,
                    new InvalidIdentityTokenException("Missing Google identity token"));
            return;
        }

        try {
            AuthenticatedUser authenticatedUser = identityTokenVerifier.verify(token);
            userProvisioningService.ensureUserExists(authenticatedUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidIdentityTokenException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        }
    }
}