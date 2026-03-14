package ieti.JobSwipe.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.repository.UserRepository;

@Service
public class UserProvisioningService {

    private static final String OAUTH_PASSWORD_PLACEHOLDER = "GOOGLE_OAUTH";

    private final UserRepository userRepository;

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void ensureUserExists(AuthenticatedUser authenticatedUser) {
        User user = userRepository.findByEmail(authenticatedUser.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(authenticatedUser.name() != null ? authenticatedUser.name() : authenticatedUser.email())
                        .email(authenticatedUser.email())
                        .password(OAUTH_PASSWORD_PLACEHOLDER)
                        .role(Role.CANDIDATE)
                        .build()));

        if (authenticatedUser.name() != null && !authenticatedUser.name().equals(user.getName())) {
            user.setName(authenticatedUser.name());
            userRepository.save(user);
        }
    }
}