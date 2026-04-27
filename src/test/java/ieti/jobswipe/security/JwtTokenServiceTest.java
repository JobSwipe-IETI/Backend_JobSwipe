package ieti.jobswipe.security;

import ieti.jobswipe.config.JwtProperties;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    @Test
    void shouldGenerateTokenWithExpectedClaims() {
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        Jwt encodedJwt = mock(Jwt.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("jobswipe");
        jwtProperties.setAccessTokenExpirationSeconds(3600L);
        jwtProperties.setSecret("01234567890123456789012345678901");

        when(encodedJwt.getTokenValue()).thenReturn("signed-token");
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        when(jwtEncoder.encode(captor.capture())).thenReturn(encodedJwt);

        JwtTokenService service = new JwtTokenService(jwtEncoder, jwtProperties);
        User user = User.builder()
                .id(7L)
                .name("Jane Doe")
                .email("jane@example.com")
                .googleId("google-123")
                .avatarUrl("https://avatar")
                .role(Role.COMPANY)
                .build();

        JwtTokenService.TokenPayload tokenPayload = service.generateToken(user);

        assertEquals("signed-token", tokenPayload.accessToken());
        assertEquals(3600L, tokenPayload.expiresIn());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertEquals("jobswipe", claims.getClaims().get("iss"));
        assertEquals("7", claims.getSubject());
        assertEquals("jane@example.com", claims.getClaim("email"));
        assertEquals("Jane Doe", claims.getClaim("name"));
        assertEquals("COMPANY", claims.getClaim("role"));
        assertEquals("google-123", claims.getClaim("googleId"));
        assertEquals("https://avatar", claims.getClaim("avatarUrl"));
        assertTrue(claims.getExpiresAt().isAfter(claims.getIssuedAt()));

        verify(jwtEncoder).encode(captor.getValue());
    }
}

