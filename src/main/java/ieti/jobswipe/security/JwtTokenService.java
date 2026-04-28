package ieti.jobswipe.security;

import ieti.jobswipe.config.JwtProperties;
import ieti.jobswipe.model.entity.User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

    private static final String TOKEN_USE_ACCESS = "access";
    private static final String TOKEN_USE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public TokenPayload generateToken(User user) {
        return generateAccessToken(user);
    }

    public TokenPayload generateAccessToken(User user) {
        return buildToken(
                user,
                jwtProperties.getAccessTokenExpirationSeconds(),
                true,
                TOKEN_USE_ACCESS);
    }

    public TokenPayload generateRefreshToken(User user) {
        return buildToken(
                user,
                jwtProperties.getRefreshTokenExpirationSeconds(),
                false,
                TOKEN_USE_REFRESH);
    }

    private TokenPayload buildToken(User user, Long expirationSeconds, boolean includeProfileClaims, String tokenUse) {
        Instant issuedAt = Instant.now();
        long effectiveExpirationSeconds = expirationSeconds != null ? expirationSeconds : 0L;
        Instant expiresAt = issuedAt.plusSeconds(effectiveExpirationSeconds);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("token_use", tokenUse);

        if (includeProfileClaims) {
            claimsBuilder
                    .claim("email", user.getEmail())
                    .claim("name", user.getName())
                    .claim("role", user.getRole().name())
                    .claim("googleId", user.getGoogleId())
                    .claim("avatarUrl", user.getAvatarUrl());
        }

        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
        return new TokenPayload(token, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());
    }

    public record TokenPayload(String accessToken, long expiresIn) {
    }
}

