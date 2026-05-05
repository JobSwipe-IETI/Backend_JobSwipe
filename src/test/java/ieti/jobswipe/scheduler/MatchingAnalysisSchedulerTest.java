package ieti.jobswipe.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.MatchingAnalysis;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.matching.MatchingAnalysisRepository;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.service.vacancy.VacancyService;

@ExtendWith(MockitoExtension.class)
class MatchingAnalysisSchedulerTest {

    @Mock
    private MatchingAnalysisRepository matchingAnalysisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VacancyService vacancyService;

    @InjectMocks
    private MatchingAnalysisScheduler scheduler;

    @Test
    void shouldCreateAnalysisRecordsForPremiumCandidatesAndWarmCache() {
        User premiumCandidate = User.builder()
                .id(10L)
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();
        MatchingAnalysis analysis = MatchingAnalysis.builder()
                .id(99L)
                .user(premiumCandidate)
                .status("PENDING")
                .analysisCount(0)
                .build();

        when(userRepository.findAll()).thenReturn(List.of(premiumCandidate));
        when(matchingAnalysisRepository.findByUser(premiumCandidate)).thenReturn(Optional.empty());
        when(matchingAnalysisRepository.findPendingPremiumAnalyses(any())).thenReturn(List.of(analysis));
        when(matchingAnalysisRepository.save(any(MatchingAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(vacancyService.warmRecommendationCacheForUser(10L)).thenReturn(3);

        scheduler.scheduleMatchingAnalysis();

        verify(vacancyService, timeout(2000).times(1)).warmRecommendationCacheForUser(10L);
        verify(matchingAnalysisRepository, timeout(2000).times(3)).save(any(MatchingAnalysis.class));
    }

    @Test
    void shouldSkipNonCandidateUsersWhenEnsuringAnalysisExists() {
        User premiumCompany = User.builder()
                .id(20L)
                .role(Role.COMPANY)
                .isPremium(true)
                .build();

        scheduler.ensureMatchingAnalysisExists(premiumCompany);

        verify(matchingAnalysisRepository, never()).findByUser(any());
        verify(matchingAnalysisRepository, never()).save(any());
    }

    @Test
    void shouldCreatePendingAnalysisRecordForEligiblePremiumCandidate() {
        User premiumCandidate = User.builder()
                .id(30L)
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();
        when(matchingAnalysisRepository.findByUser(premiumCandidate)).thenReturn(Optional.empty());
        when(matchingAnalysisRepository.save(any(MatchingAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.ensureMatchingAnalysisExists(premiumCandidate);

        ArgumentCaptor<MatchingAnalysis> captor = ArgumentCaptor.forClass(MatchingAnalysis.class);
        verify(matchingAnalysisRepository).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getAnalysisCount());
        assertEquals(premiumCandidate, captor.getValue().getUser());
    }
}
