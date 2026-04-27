package ieti.jobswipe.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.repository.UserRepository;

@Service
public class UserProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(UserProvisioningService.class);
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(2);

    private final UserRepository userRepository;
    private final Map<String, CachedUserSnapshot> googleUserCache = new ConcurrentHashMap<>();
    private final Map<String, CachedUserSnapshot> emailUserCache = new ConcurrentHashMap<>();

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User ensureUserExists(AuthenticatedUser authenticatedUser) {
        CachedUserSnapshot cached = getCachedUser(authenticatedUser.subject(), authenticatedUser.email());
        if (cached != null) {
            User cachedUser = cached.toUser();
            boolean changed = applyTokenData(cachedUser, authenticatedUser);
            long saveStart = System.nanoTime();
            User result = changed ? userRepository.save(cachedUser) : cachedUser;
            long saveMs = (System.nanoTime() - saveStart) / 1_000_000;
            cacheUser(result);
            logger.info("⏱️ UserProvisioningService.ensureUserExists lookup=0 ms save={} ms changed={} cacheHit=true userId={}",
                saveMs,
                    changed,
                    result.getId());
            return result;
        }

        long lookupStart = System.nanoTime();
        User user = userRepository.findFirstByGoogleIdOrEmail(
                        authenticatedUser.subject(),
                        authenticatedUser.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(authenticatedUser.name() != null ? authenticatedUser.name() : authenticatedUser.email())
                        .email(authenticatedUser.email())
                        .googleId(authenticatedUser.subject())
                        .avatarUrl(authenticatedUser.picture())
                        .password(null)
                        .role(Role.CANDIDATE)
                        .build()));
        long lookupMs = (System.nanoTime() - lookupStart) / 1_000_000;

        boolean changed = applyTokenData(user, authenticatedUser);

        long saveStart = System.nanoTime();
        User result = changed ? userRepository.save(user) : user;
        long saveMs = (System.nanoTime() - saveStart) / 1_000_000;
        cacheUser(result);
        logger.info("⏱️ UserProvisioningService.ensureUserExists lookup={} ms save={} ms changed={} userId={}",
                lookupMs, saveMs, changed, result.getId());
        return result;
    }

    private boolean applyTokenData(User user, AuthenticatedUser authenticatedUser) {
        boolean changed = false;

        if (!authenticatedUser.subject().equals(user.getGoogleId())) {
            user.setGoogleId(authenticatedUser.subject());
            changed = true;
        }

        if (authenticatedUser.name() != null && !authenticatedUser.name().equals(user.getName())) {
            user.setName(authenticatedUser.name());
            changed = true;
        }

        if (authenticatedUser.picture() != null && !authenticatedUser.picture().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(authenticatedUser.picture());
            changed = true;
        }

        return changed;
    }

    private CachedUserSnapshot getCachedUser(String googleId, String email) {
        Instant now = Instant.now();

        CachedUserSnapshot byGoogle = googleUserCache.get(googleId);
        if (byGoogle != null) {
            if (byGoogle.expiresAt().isAfter(now)) {
                return byGoogle;
            }
            googleUserCache.remove(googleId);
        }

        String normalizedEmail = normalizeEmail(email);
        CachedUserSnapshot byEmail = emailUserCache.get(normalizedEmail);
        if (byEmail != null) {
            if (byEmail.expiresAt().isAfter(now)) {
                return byEmail;
            }
            emailUserCache.remove(normalizedEmail);
        }

        return null;
    }

    private void cacheUser(User user) {
        Instant expiresAt = Instant.now().plus(USER_CACHE_TTL);
        CachedUserSnapshot snapshot = new CachedUserSnapshot(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getGoogleId(),
                user.getAvatarUrl(),
                user.getRole(),
                expiresAt);

        if (user.getGoogleId() != null && !user.getGoogleId().isBlank()) {
            googleUserCache.put(user.getGoogleId(), snapshot);
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailUserCache.put(normalizeEmail(user.getEmail()), snapshot);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private record CachedUserSnapshot(
            Long id,
            String name,
            String email,
            String googleId,
            String avatarUrl,
            Role role,
            Instant expiresAt) {

        User toUser() {
            return User.builder()
                    .id(id)
                    .name(name)
                    .email(email)
                    .googleId(googleId)
                    .avatarUrl(avatarUrl)
                    .password(null)
                    .role(role)
                    .build();
        }
    }
}

