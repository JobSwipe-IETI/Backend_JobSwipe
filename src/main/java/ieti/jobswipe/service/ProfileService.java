package ieti.jobswipe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ieti.jobswipe.dto.CandidateProfileRequest;
import ieti.jobswipe.dto.CompanyProfileRequest;
import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.ProfileNotFoundException;
import ieti.jobswipe.exception.UserNotFoundException;
import ieti.jobswipe.model.CandidateProfile;
import ieti.jobswipe.model.CompanyProfile;
import ieti.jobswipe.model.Profile;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.User;
import ieti.jobswipe.repository.CandidateProfileRepository;
import ieti.jobswipe.repository.CompanyProfileRepository;
import ieti.jobswipe.repository.ProfileRepository;
import ieti.jobswipe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
@Transactional
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProfileRepository profileRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;

    public ProfileService(ProfileRepository profileRepository,
            CandidateProfileRepository candidateProfileRepository,
            CompanyProfileRepository companyProfileRepository,
            UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.userRepository = userRepository;
    }

    public Profile getProfileByUserId(Long userId) {
        logger.info("🔎 ProfileService.getProfileByUserId called with userId={}", userId);
        var profile = profileRepository.findByUserId(userId);
        logger.info("📊 findByUserId({}) returned: {}", userId, profile.isPresent() ? "FOUND" : "NOT FOUND");
        
        // Inicializar los campos LOB dentro de la transacción
        Profile result = profile.orElseThrow(() -> new ProfileNotFoundException(ErrorMessages.PROFILE_NOT_FOUND));
        // Acceder a los campos LOB para provocar que se carguen
        String skills = result.getSkills();
        String experience = result.getExperience();
        String education = result.getEducation();
        logger.info("✅ LOB fields initialized: skills={}, experience={}, education={}", 
            skills != null ? "OK" : "NULL", 
            experience != null ? "OK" : "NULL",
            education != null ? "OK" : "NULL");
        
        return result;
    }

    public Profile upsertCandidateProfile(Long userId, CandidateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));

        Profile profile = profileRepository.findByUserId(userId)
                .orElse(Profile.builder().user(user).build());

        user.setName(request.getDisplayName());
        user.setRole(Role.CANDIDATE);
        userRepository.save(user);

        profile.setProfessionalTitle(request.getProfessionalTitle());
        profile.setSummary(request.getSummary());
        profile.setSkills(serializeToJson(request.getSkills()));
        profile.setExperience(serializeToJson(request.getExperiences()));
        profile.setEducation(request.getEducation());
        profile.setLocation(request.getLocation());
        profile.setNationality(request.getNationality());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setOnboardingCompleted(true);

        profile = profileRepository.save(profile);

        if (profile.getCompanyProfile() != null) {
            companyProfileRepository.delete(profile.getCompanyProfile());
            profile.setCompanyProfile(null);
        }

        CandidateProfile candidateProfile = profile.getCandidateProfile();
        if (candidateProfile == null) {
            candidateProfile = CandidateProfile.builder().profile(profile).build();
        }

        candidateProfile.setLanguages(request.getLanguages());
        candidateProfile.setExpectedSalary(request.getExpectedSalary());
        candidateProfile.setAvailability(request.getAvailability());
        candidateProfile.setSector(request.getSector());
        candidateProfile.setGithubUrl(request.getGithubUrl());
        candidateProfile.setLinkedinUrl(request.getLinkedinUrl());
        candidateProfile.setPortfolioUrl(request.getPortfolioUrl());

        candidateProfile = candidateProfileRepository.save(candidateProfile);
        profile.setCandidateProfile(candidateProfile);
        return profileRepository.save(profile);
    }

    public Profile upsertCompanyProfile(Long userId, CompanyProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));

        Profile profile = profileRepository.findByUserId(userId)
                .orElse(Profile.builder().user(user).build());

        user.setRole(Role.COMPANY);
        userRepository.save(user);

        profile.setProfessionalTitle(request.getCompanyName());
        profile.setSummary(request.getCompanyDescription());
        profile.setLocation(request.getHeadquartersLocation());
        profile.setNationality(request.getNationality());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setOnboardingCompleted(true);

        profile = profileRepository.save(profile);

        if (profile.getCandidateProfile() != null) {
            candidateProfileRepository.delete(profile.getCandidateProfile());
            profile.setCandidateProfile(null);
        }

        CompanyProfile companyProfile = profile.getCompanyProfile();
        if (companyProfile == null) {
            companyProfile = CompanyProfile.builder().profile(profile).build();
        }

        companyProfile.setCompanyName(request.getCompanyName());
        companyProfile.setCompanyDescription(request.getCompanyDescription());
        companyProfile.setLegalId(request.getLegalId());
        companyProfile.setIndustry(request.getIndustry());
        companyProfile.setCompanySize(request.getCompanySize());
        companyProfile.setWebsite(request.getWebsite());
        companyProfile.setHeadquartersLocation(request.getHeadquartersLocation());
        companyProfile.setHiringContactName(request.getHiringContactName());
        companyProfile.setHiringContactEmail(request.getHiringContactEmail());

        companyProfile = companyProfileRepository.save(companyProfile);
        profile.setCompanyProfile(companyProfile);
        return profileRepository.save(profile);
    }

    public Long resolveEffectiveUserId(
            Long requestedUserId,
            String email,
            String googleId,
            String name,
            String avatarUrl,
            Role fallbackRole) {
        final Optional<User> byRequestedId = requestedUserId == null
                ? Optional.empty()
                : userRepository.findById(requestedUserId);

        if (byRequestedId.isPresent()) {
            return byRequestedId.get().getId();
        }

        final Optional<User> byGoogleId = StringUtils.hasText(googleId)
                ? userRepository.findByGoogleId(googleId)
                : Optional.empty();

        if (byGoogleId.isPresent()) {
            return byGoogleId.get().getId();
        }

        final Optional<User> byEmail = StringUtils.hasText(email)
                ? userRepository.findByEmail(email)
                : Optional.empty();

        if (byEmail.isPresent()) {
            return byEmail.get().getId();
        }

        if (!StringUtils.hasText(email)) {
            throw new UserNotFoundException(ErrorMessages.USER_NOT_FOUND);
        }

        final User createdUser = userRepository.save(User.builder()
                .name(StringUtils.hasText(name) ? name : email)
                .email(email)
                .googleId(StringUtils.hasText(googleId) ? googleId : null)
                .avatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl : null)
                .password(null)
                .role(fallbackRole)
                .build());

        logger.warn("⚠️ Recreated missing user from JWT claims. requestedUserId={}, newUserId={}, email={}",
                requestedUserId, createdUser.getId(), email);

        return createdUser.getId();
    }

    private String serializeToJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize profile payload", exception);
        }
    }
}

