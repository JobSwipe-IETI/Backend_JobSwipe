package ieti.JobSwipe.service;

import ieti.JobSwipe.dto.CandidateProfileRequest;
import ieti.JobSwipe.dto.CompanyProfileRequest;
import ieti.JobSwipe.exception.ErrorMessages;
import ieti.JobSwipe.exception.ProfileNotFoundException;
import ieti.JobSwipe.exception.UserNotFoundException;
import ieti.JobSwipe.model.CandidateProfile;
import ieti.JobSwipe.model.CompanyProfile;
import ieti.JobSwipe.model.Profile;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.repository.CandidateProfileRepository;
import ieti.JobSwipe.repository.CompanyProfileRepository;
import ieti.JobSwipe.repository.ProfileRepository;
import ieti.JobSwipe.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

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
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException(ErrorMessages.PROFILE_NOT_FOUND));
    }

    public Profile upsertCandidateProfile(Long userId, CandidateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessages.USER_NOT_FOUND));

        Profile profile = profileRepository.findByUserId(userId)
                .orElse(Profile.builder().user(user).build());

        user.setRole(Role.CANDIDATE);
        userRepository.save(user);

        profile.setProfessionalTitle(request.getProfessionalTitle());
        profile.setSummary(request.getSummary());
        profile.setSkills(request.getSkills());
        profile.setExperience(request.getExperience());
        profile.setEducation(request.getEducation());
        profile.setLocation(request.getLocation());
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
        candidateProfile.setPortfolioUrl(request.getPortfolioUrl());
        candidateProfile.setCvUrl(request.getCvUrl());

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
}
