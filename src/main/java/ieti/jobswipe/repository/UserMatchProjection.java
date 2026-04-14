package ieti.jobswipe.repository;

import java.time.LocalDateTime;

public interface UserMatchProjection {
    Long getVacancyId();

    String getVacancyTitle();

    Long getCounterpartId();

    String getCounterpartName();

    LocalDateTime getMatchedAt();

    Float getCompatibilityPercentage();

    String getCompatibilityLevel();
}