package ieti.jobswipe.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import ieti.jobswipe.dto.VacancyRecommendationResponse;
import jakarta.annotation.PreDestroy;

@Service
public class RecommendationJobService {

    public enum JobStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static class RecommendationJob {
        private final String jobId;
        private final Long userId;
        private final Instant startedAt;

        private volatile JobStatus status;
        private volatile int processed;
        private volatile int total;
        private volatile String message;
        private volatile String error;
        private volatile List<VacancyRecommendationResponse> result;

        public RecommendationJob(String jobId, Long userId) {
            this.jobId = jobId;
            this.userId = userId;
            this.startedAt = Instant.now();
            this.status = JobStatus.RUNNING;
            this.processed = 0;
            this.total = 0;
            this.message = "Iniciando...";
            this.result = List.of();
        }

        public String getJobId() {
            return jobId;
        }

        public Long getUserId() {
            return userId;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public JobStatus getStatus() {
            return status;
        }

        public int getProcessed() {
            return processed;
        }

        public int getTotal() {
            return total;
        }

        public String getMessage() {
            return message;
        }

        public String getError() {
            return error;
        }

        public List<VacancyRecommendationResponse> getResult() {
            return result;
        }

        public int getProgressPercent() {
            if (total <= 0) {
                return status == JobStatus.COMPLETED ? 100 : 0;
            }
            int value = (int) Math.round((processed * 100.0) / total);
            return Math.max(0, Math.min(100, value));
        }

        private void setProgress(int processed, int total, String message) {
            this.processed = processed;
            this.total = total;
            this.message = message;
        }

        private void complete(List<VacancyRecommendationResponse> result) {
            this.result = new ArrayList<>(result);
            this.status = JobStatus.COMPLETED;
            this.message = "Recomendaciones listas";
            this.processed = this.total > 0 ? this.total : this.processed;
        }

        private void fail(String error) {
            this.status = JobStatus.FAILED;
            this.error = error;
            this.message = "No se pudieron generar recomendaciones";
        }
    }

    private final VacancyService vacancyService;
    private final Map<String, RecommendationJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public RecommendationJobService(VacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    public String startJob(Long userId, Float minScore, Integer limit) {
        String jobId = UUID.randomUUID().toString();
        RecommendationJob job = new RecommendationJob(jobId, userId);
        jobs.put(jobId, job);

        executorService.submit(() -> {
            try {
                List<VacancyRecommendationResponse> recommendations = vacancyService.getRecommendedVacanciesWithProgress(
                        userId,
                        minScore,
                        limit,
                        job::setProgress);
                job.complete(recommendations);
            } catch (Exception ex) {
                job.fail(ex.getMessage());
            }
        });

        return jobId;
    }

    public Optional<RecommendationJob> getJob(String jobId, Long userId) {
        RecommendationJob job = jobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
