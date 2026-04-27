package ieti.jobswipe.service.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.when;

import ieti.jobswipe.dto.vacancy.VacancyRecommendationResponse;
import java.util.List;
import java.util.Optional;
import ieti.jobswipe.service.recommendation.RecommendationJobService;
import ieti.jobswipe.service.vacancy.VacancyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationJobServiceTest {

    @Mock
    private VacancyService vacancyService;

    @InjectMocks
    private RecommendationJobService recommendationJobService;

    @AfterEach
    void tearDown() {
        recommendationJobService.shutdown();
    }

    @Test
    void shouldStartJobAndCompleteSuccessfully() {
        List<VacancyRecommendationResponse> expected = List.of(
                VacancyRecommendationResponse.builder().vacancyId(1L).title("Backend Developer").build());

        when(vacancyService.getRecommendedVacanciesWithProgress(anyLong(), anyFloat(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    VacancyService.RecommendationProgressListener listener = invocation.getArgument(3);
                    listener.onProgress(1, 1, "Done");
                    return expected;
                });

        String jobId = recommendationJobService.startJob(1L, 0f, 10);
        RecommendationJobService.RecommendationJob job = waitForJob(jobId, 1L);

        assertEquals(RecommendationJobService.JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgressPercent());
        assertEquals(expected, job.getResult());
    }

    @Test
    void shouldMarkJobAsFailedWhenServiceThrows() {
        when(vacancyService.getRecommendedVacanciesWithProgress(anyLong(), anyFloat(), anyInt(), any()))
                .thenThrow(new RuntimeException("matching failed"));

        String jobId = recommendationJobService.startJob(1L, 0f, 10);
        RecommendationJobService.RecommendationJob job = waitForJob(jobId, 1L);

        assertEquals(RecommendationJobService.JobStatus.FAILED, job.getStatus());
        assertNotNull(job.getError());
        assertTrue(job.getError().contains("matching failed"));
    }

    @Test
    void shouldCollectRecommendationsThroughCallback() {
        List<VacancyRecommendationResponse> expected = List.of(
                VacancyRecommendationResponse.builder().vacancyId(2L).title("Frontend").build());

        when(vacancyService.getRecommendedVacanciesWithProgress(anyLong(), anyFloat(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    VacancyService.RecommendationProgressListener listener = invocation.getArgument(3);
                    listener.onRecommendation(expected.get(0));
                    return expected;
                });

        String jobId = recommendationJobService.startJob(1L, 0f, 10);
        RecommendationJobService.RecommendationJob job = waitForJob(jobId, 1L);

        assertEquals(RecommendationJobService.JobStatus.COMPLETED, job.getStatus());
        assertEquals(expected, job.getResult());
        assertEquals(100, job.getProgressPercent());
    }

    @Test
    void shouldReportZeroProgressWhenTotalIsZeroAndJobRunning() {
        RecommendationJobService.RecommendationJob job = new RecommendationJobService.RecommendationJob("job-1", 1L);

        assertEquals(0, job.getProgressPercent());
    }

    @Test
    void shouldReportFullProgressWhenTotalIsZeroAndJobCompleted() throws Exception {
        RecommendationJobService.RecommendationJob job = new RecommendationJobService.RecommendationJob("job-2", 1L);
        java.lang.reflect.Field statusField = RecommendationJobService.RecommendationJob.class.getDeclaredField("status");
        statusField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<RecommendationJobService.JobStatus> status =
                (java.util.concurrent.atomic.AtomicReference<RecommendationJobService.JobStatus>) statusField.get(job);
        status.set(RecommendationJobService.JobStatus.COMPLETED);

        assertEquals(100, job.getProgressPercent());
    }

    @Test
    void shouldReturnEmptyWhenUserDoesNotOwnJob() {
        when(vacancyService.getRecommendedVacanciesWithProgress(anyLong(), anyFloat(), anyInt(), any()))
                .thenReturn(List.of());

        String jobId = recommendationJobService.startJob(1L, 0f, 10);
        waitForJob(jobId, 1L);

        Optional<RecommendationJobService.RecommendationJob> otherUserView = recommendationJobService.getJob(jobId, 2L);

        assertFalse(otherUserView.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenJobDoesNotExist() {
        Optional<RecommendationJobService.RecommendationJob> result = recommendationJobService.getJob("missing", 1L);

        assertFalse(result.isPresent());
    }

    private RecommendationJobService.RecommendationJob waitForJob(String jobId, Long userId) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            Optional<RecommendationJobService.RecommendationJob> jobOpt = recommendationJobService.getJob(jobId, userId);
            if (jobOpt.isPresent()) {
                RecommendationJobService.RecommendationJob job = jobOpt.get();
                if (job.getStatus() != RecommendationJobService.JobStatus.RUNNING) {
                    return job;
                }
            }
            Thread.yield();
        }
        return recommendationJobService.getJob(jobId, userId)
                .orElseThrow(() -> new IllegalStateException("Job not found after waiting"));
    }
}
