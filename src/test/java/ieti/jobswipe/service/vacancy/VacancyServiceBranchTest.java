package ieti.jobswipe.service.vacancy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ieti.jobswipe.dto.company.CompanyCandidateDecisionRequest;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.repository.recommendation.RecommendationCacheRepository;
import ieti.jobswipe.repository.company.CompanyCandidateDecisionRepository;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.repository.vacancy.VacancyRepository;
import ieti.jobswipe.repository.vacancy.VacancySwipeRepository;
import ieti.jobswipe.service.chat.ChatRealtimeService;
import ieti.jobswipe.service.matching.MatchingService;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class VacancyServiceBranchTest {

    @Mock
    VacancyRepository vacancyRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    MatchingService matchingService;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    RecommendationCacheRepository recommendationCacheRepository;

    @Mock
    VacancySwipeRepository vacancySwipeRepository;

    @Mock
    CompanyCandidateDecisionRepository companyCandidateDecisionRepository;

    @Mock
    CacheManager cacheManager;

    @Mock
    ChatRealtimeService chatRealtimeService;

    private VacancyService subject;

    @BeforeEach
    void setUp() {
        subject = new VacancyService(
                vacancyRepository,
                userRepository,
                matchingService,
                profileRepository,
                recommendationCacheRepository,
                vacancySwipeRepository,
                companyCandidateDecisionRepository,
                cacheManager);
        // inject chatRealtimeService into private autowired field
        ReflectionTestUtils.setField(subject, "chatRealtimeService", chatRealtimeService);
    }

    @Test
    void shouldPublishNotificationsWhenRegisterSwipeDecisionMatched() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(10L);
        User company = new User();
        company.setId(5L);
        company.setName("Comp");
        vacancy.setCompany(company);

        when(vacancyRepository.findById(10L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
                5L, 2L, 10L, SwipeDecisionType.LIKE)).thenReturn(true);

        // call: userId=2, vacancyId=10, decision=LIKE
        subject.registerSwipeDecision(2L, 10L, SwipeDecisionType.LIKE);

        // Expect three notification publishes: company_like + candidate match + company match
        verify(chatRealtimeService, times(3)).publishToUser(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPublishNotificationsWhenRegisterCompanyCandidateDecisionMatched() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(20L);
        User company = new User();
        company.setId(7L);
        company.setName("Comp");
        company.setRole(ieti.jobswipe.model.Role.COMPANY);
        vacancy.setCompany(company);

        User candidate = new User();
        candidate.setId(3L);
        candidate.setName("Cand");
        candidate.setRole(ieti.jobswipe.model.Role.CANDIDATE);

        when(userRepository.findById(7L)).thenReturn(Optional.of(company));
        when(userRepository.findById(3L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(20L)).thenReturn(Optional.of(vacancy));
        when(vacancySwipeRepository.existsByUserIdAndVacancyIdAndDecision(3L, 20L, SwipeDecisionType.LIKE))
                .thenReturn(true);

        subject.registerCompanyCandidateDecision(
            7L,
            20L,
            3L,
            CompanyCandidateDecisionRequest.builder()
                .decision(SwipeDecisionType.LIKE)
                .build());

        // Expect notification for company_decision + two match notifications
        verify(chatRealtimeService, times(3)).publishToUser(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void evictCacheKeyHandlesNullsGracefully() throws Exception {
        // create instance with null cacheManager to trigger early return
        VacancyService svcWithNullCache = new VacancyService(
                vacancyRepository,
                userRepository,
                matchingService,
                profileRepository,
                recommendationCacheRepository,
                vacancySwipeRepository,
                companyCandidateDecisionRepository,
                null);

        Method evict = VacancyService.class.getDeclaredMethod("evictCacheKey", String.class, Object.class);
        evict.setAccessible(true);

        // key == null branch
        assertDoesNotThrow(() -> evict.invoke(svcWithNullCache, "someCache", null));

        // cacheManager == null branch (key non-null)
        assertDoesNotThrow(() -> evict.invoke(svcWithNullCache, "someCache", 123L));
    }
}
