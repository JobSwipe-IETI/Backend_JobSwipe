package ieti.jobswipe.service.profile;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.dto.company.CompanyProfileRequest;
import ieti.jobswipe.dto.profile.CandidateProfileRequest;
import ieti.jobswipe.exception.ErrorMessages;
import ieti.jobswipe.exception.ProfileNotFoundException;
import ieti.jobswipe.exception.UserNotFoundException;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.CandidateProfile;
import ieti.jobswipe.model.entity.CompanyProfile;
import ieti.jobswipe.model.entity.Profile;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.company.CompanyProfileRepository;
import ieti.jobswipe.repository.profile.CandidateProfileRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.user.UserRepository;

@Service
@Transactional
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProfileRepository profileRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;
    private final ProfileFeedbackService profileFeedbackService;

    public ProfileService(ProfileRepository profileRepository,
            CandidateProfileRepository candidateProfileRepository,
            CompanyProfileRepository companyProfileRepository,
            UserRepository userRepository,
            ProfileFeedbackService profileFeedbackService) {
        this.profileRepository = profileRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.userRepository = userRepository;
        this.profileFeedbackService = profileFeedbackService;
    }

    @Transactional(readOnly = true)
    public Profile getProfileByUserId(Long userId) {
        long queryStart = System.nanoTime();
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new ProfileNotFoundException(ErrorMessages.PROFILE_NOT_FOUND));
        long queryMs = (System.nanoTime() - queryStart) / 1_000_000;
        logger.info("⏱️ ProfileService.findByUserId userId={} took {} ms", userId, queryMs);
        return profile;
    }

    @Transactional(readOnly = true)
    public boolean hasProfileByUserId(Long userId) {
        return profileRepository.existsByUserId(userId);
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
        Profile saved = profileRepository.save(profile);

        // Fire-and-forget analysis: call AI service asynchronously
        try {
            logger.info("🔄 Dispatching profile analysis after candidate profile save for userId={} profileId={}",
                    userId, saved.getId());
            profileFeedbackService.analyzeAndSaveAsync(saved.getId());
        } catch (Exception e) {
            logger.warn("Failed to dispatch profile analysis async: {}", e.getMessage());
        }

        return saved;
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

