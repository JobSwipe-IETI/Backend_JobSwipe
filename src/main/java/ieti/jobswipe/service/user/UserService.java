package ieti.jobswipe.service.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.UserNotFoundException;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.scheduler.MatchingAnalysisScheduler;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MatchingAnalysisScheduler matchingAnalysisScheduler;

    public UserService(UserRepository userRepository, MatchingAnalysisScheduler matchingAnalysisScheduler) {
        this.userRepository = userRepository;
        this.matchingAnalysisScheduler = matchingAnalysisScheduler;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }

    public User createUser(User user) {
        User created = userRepository.save(user);
        if (Boolean.TRUE.equals(created.getIsPremium())) {
            matchingAnalysisScheduler.ensureMatchingAnalysisExists(created);
        }
        return created;
    }

    public User updateUser(Long id, User user) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));

        existingUser.setName(user.getName());
        existingUser.setEmail(user.getEmail());
        existingUser.setPassword(user.getPassword());
        existingUser.setGoogleId(user.getGoogleId());
        existingUser.setAvatarUrl(user.getAvatarUrl());
        existingUser.setRole(user.getRole());

        return userRepository.save(existingUser);
    }

    @Transactional
    public User updateUserRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserPremiumStatus(Long userId, Boolean isPremium) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));
        
        user.setIsPremium(isPremium != null ? isPremium : false);
        User updated = userRepository.save(user);
        
        // Ensure MatchingAnalysis record exists if user becomes premium
        if (updated.getIsPremium()) {
            matchingAnalysisScheduler.ensureMatchingAnalysisExists(updated);
        }
        
        return updated;
    }

    public void deleteUser(Long id) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));
        userRepository.delete(existingUser);
    }
}

