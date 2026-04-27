package ieti.jobswipe.dto;

import java.util.List;

public record VacancyDetailResponse(
        Long id,
        String title,
        String description,
        String location,
        String sector,
        String modality,
        String employmentType,
        String experienceLevel,
        List<String> technologies,
        List<String> softSkills,
        List<String> responsibilities,
        List<String> technicalRequirements,
        Double minSalary,
        Double maxSalary,
        List<String> benefits,
        Long companyId,
        String companyName) {
}