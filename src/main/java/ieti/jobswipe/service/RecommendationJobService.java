package ieti.jobswipe.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

        private final AtomicReference<JobStatus> status;
        private final AtomicInteger processed;
        private final AtomicInteger total;
        private final AtomicReference<String> message;
        private final AtomicReference<String> error;
        private final AtomicReference<List<VacancyRecommendationResponse>> result;

        public RecommendationJob(String jobId, Long userId) {
            this.jobId = jobId;
            this.userId = userId;
            this.startedAt = Instant.now();
            this.status = new AtomicReference<>(JobStatus.RUNNING);
            this.processed = new AtomicInteger(0);
            this.total = new AtomicInteger(0);
            this.message = new AtomicReference<>("Iniciando...");
            this.error = new AtomicReference<>(null);
            this.result = new AtomicReference<>(List.of());
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
            return status.get();
        }

        public int getProcessed() {
            return processed.get();
        }

        public int getTotal() {
            return total.get();
        }

        public String getMessage() {
            return message.get();
        }

        public String getError() {
            return error.get();
        }

        public List<VacancyRecommendationResponse> getResult() {
            return result.get();
        }

        public int getProgressPercent() {
            int totalValue = total.get();
            if (totalValue <= 0) {
                return status.get() == JobStatus.COMPLETED ? 100 : 0;
            }
            int processedValue = processed.get();
            int value = (int) Math.round((processedValue * 100.0) / totalValue);
            return Math.max(0, Math.min(100, value));
        }

        private void setProgress(int processed, int total, String message) {
            this.processed.set(processed);
            this.total.set(total);
            this.message.set(message);
        }

        private void complete(List<VacancyRecommendationResponse> result) {
            this.result.set(List.copyOf(result));
            this.status.set(JobStatus.COMPLETED);
            this.message.set("Recomendaciones listas");
            int totalValue = this.total.get();
            if (totalValue > 0) {
                this.processed.set(totalValue);
            }
        }

        private void fail(String error) {
            this.status.set(JobStatus.FAILED);
            this.error.set(error);
            this.message.set("No se pudieron generar recomendaciones");
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
