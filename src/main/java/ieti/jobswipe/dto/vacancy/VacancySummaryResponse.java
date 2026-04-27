package ieti.jobswipe.dto.vacancy;

public record VacancySummaryResponse(
        Long id,
        String title,
        String companyName,
        String location,
        String description,
        Double minSalary,
        Double maxSalary) {
}