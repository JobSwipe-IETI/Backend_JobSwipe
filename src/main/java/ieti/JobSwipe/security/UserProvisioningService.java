package ieti.JobSwipe.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.repository.UserRepository;

@Service
public class UserProvisioningService {

    private final UserRepository userRepository;

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User ensureUserExists(AuthenticatedUser authenticatedUser) {
        User user = userRepository.findByGoogleId(authenticatedUser.subject())
                .or(() -> userRepository.findByEmail(authenticatedUser.email()))
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(authenticatedUser.name() != null ? authenticatedUser.name() : authenticatedUser.email())
                        .email(authenticatedUser.email())
                        .googleId(authenticatedUser.subject())
                        .avatarUrl(authenticatedUser.picture())
                        .password(null)
                        .role(Role.CANDIDATE)
                        .build()));

        user.setGoogleId(authenticatedUser.subject());

        if (authenticatedUser.name() != null && !authenticatedUser.name().equals(user.getName())) {
            user.setName(authenticatedUser.name());
        }

        if (authenticatedUser.picture() != null && !authenticatedUser.picture().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(authenticatedUser.picture());
        }

        return userRepository.save(user);
    }
}
