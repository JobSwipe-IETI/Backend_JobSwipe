package ieti.jobswipe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer;
    private Long accessTokenExpirationSeconds;
    private Long refreshTokenExpirationSeconds;
    private String secret;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(Long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public Long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public void setRefreshTokenExpirationSeconds(Long refreshTokenExpirationSeconds) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}

