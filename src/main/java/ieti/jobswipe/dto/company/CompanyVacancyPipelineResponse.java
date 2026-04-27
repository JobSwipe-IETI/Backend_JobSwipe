package ieti.jobswipe.dto.company;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyVacancyPipelineResponse {
    private Long vacancyId;
    private String vacancyTitle;
    private Integer applicantsCount;
}
