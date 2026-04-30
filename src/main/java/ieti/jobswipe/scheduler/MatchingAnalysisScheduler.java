package ieti.jobswipe.scheduler;

import ieti.jobswipe.model.entity.MatchingAnalysis;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.repository.matching.MatchingAnalysisRepository;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.service.vacancy.VacancyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MatchingAnalysisScheduler {

    private final MatchingAnalysisRepository matchingAnalysisRepository;
    private final UserRepository userRepository;
    private final VacancyService vacancyService;

    public MatchingAnalysisScheduler(
            MatchingAnalysisRepository matchingAnalysisRepository,
            UserRepository userRepository,
            VacancyService vacancyService
    ) {
        this.matchingAnalysisRepository = matchingAnalysisRepository;
        this.userRepository = userRepository;
        this.vacancyService = vacancyService;
    }

    /**
     * Ejecuta cada 30 segundos para verificar y ejecutar análisis automáticos
     * Solo para usuarios premium candidatos
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void scheduleMatchingAnalysis() {
        try {
            log.info("🔄 Starting automatic matching analysis scheduler");
            ensureMatchingAnalysisExistsForPremiumCandidates();

            // Revisa de nuevo cada 30 segundos; el cache evita recalcular combinaciones vigentes.
            LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(30);

            // Buscar análisis pendientes (usuarios premium sin análisis reciente)
            List<MatchingAnalysis> pendingAnalyses = matchingAnalysisRepository.findPendingPremiumAnalyses(cutoffTime);

            if (pendingAnalyses.isEmpty()) {
                log.info("✅ No pending analyses found for premium users");
                return;
            }

            log.info("📊 Found {} pending premium user analyses", pendingAnalyses.size());

            // Procesar cada análisis en background (no bloqueante)
            for (MatchingAnalysis analysis : pendingAnalyses) {
                processMatchingAnalysisAsync(analysis);
            }

        } catch (Exception e) {
            log.error("❌ Error in matching analysis scheduler", e);
        }
    }

    private void processMatchingAnalysisAsync(MatchingAnalysis analysis) {
        CompletableFuture.runAsync(() -> {
            try {
                analysis.setStatus("IN_PROGRESS");
                matchingAnalysisRepository.save(analysis);

                User user = analysis.getUser();
                log.info("🚀 Processing matching analysis for premium user: {}", user.getId());

                int processedVacancies = vacancyService.warmRecommendationCacheForUser(user.getId());

                // Update success status
                analysis.setStatus("PENDING"); // Reset to PENDING for next cycle
                analysis.setLastAnalysisAt(LocalDateTime.now());
                analysis.setAnalysisCount((analysis.getAnalysisCount() != null ? analysis.getAnalysisCount() : 0) + 1);
                matchingAnalysisRepository.save(analysis);

                log.info("✅ Matching analysis completed for user: {}. Vacancies processed: {}", user.getId(), processedVacancies);

            } catch (Exception e) {
                log.error("❌ Error processing matching analysis for user: {}", analysis.getUser().getId(), e);
                analysis.setStatus("PENDING");
                matchingAnalysisRepository.save(analysis);
            }
        });
    }

    private void ensureMatchingAnalysisExistsForPremiumCandidates() {
        for (User user : userRepository.findAll()) {
            if (Boolean.TRUE.equals(user.getIsPremium()) && user.getRole() == Role.CANDIDATE) {
                ensureMatchingAnalysisExists(user);
            }
        }
    }

    /**
     * Crea o actualiza el registro de MatchingAnalysis para un usuario premium
     */
    public void ensureMatchingAnalysisExists(User user) {
        if (!Boolean.TRUE.equals(user.getIsPremium()) || user.getRole() != Role.CANDIDATE) {
            return;
        }

        matchingAnalysisRepository.findByUser(user)
                .ifPresentOrElse(
                        analysis -> {
                            // Ya existe, no hacer nada
                            log.debug("MatchingAnalysis already exists for user: {}", user.getId());
                        },
                        () -> {
                            // Crear nuevo
                            MatchingAnalysis analysis = MatchingAnalysis.builder()
                                    .user(user)
                                    .status("PENDING")
                                    .analysisCount(0)
                                    .build();
                            matchingAnalysisRepository.save(analysis);
                            log.info("✅ Created MatchingAnalysis record for premium user: {}", user.getId());
                        }
                );
    }
}
