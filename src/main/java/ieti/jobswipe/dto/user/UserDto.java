package ieti.jobswipe.dto.user;

import java.time.LocalDateTime;

import ieti.jobswipe.model.Role;

public record UserDto(
        Long id,
        String name,
        String email,
        String googleId,
        String avatarUrl,
        Role role,
        Boolean isPremium,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}