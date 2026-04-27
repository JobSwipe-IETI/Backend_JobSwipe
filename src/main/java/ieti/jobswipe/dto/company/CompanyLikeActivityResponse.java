package ieti.jobswipe.dto.company;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyLikeActivityResponse {
    private Long vacancyId;
    private String vacancyTitle;
    private Long candidateId;
    private String candidateName;
    private LocalDateTime likedAt;
}
