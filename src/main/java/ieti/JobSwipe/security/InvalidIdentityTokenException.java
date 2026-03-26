package ieti.JobSwipe.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidIdentityTokenException extends AuthenticationException {

    public InvalidIdentityTokenException(String message) {
        super(message);
    }

    public InvalidIdentityTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}