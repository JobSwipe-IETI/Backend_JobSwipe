package IETI.JobSwipe.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenUserInfoService {

    public UserProfileResponse extractUserInfo(Jwt jwt) {
        return new UserProfileResponse(
            jwt.getSubject(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name")
        );
    }
}
