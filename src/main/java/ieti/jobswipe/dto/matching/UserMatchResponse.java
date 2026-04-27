package ieti.jobswipe.dto.matching;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMatchResponse {
    private Long vacancyId;
    private String vacancyTitle;
    private Long counterpartId;
    private String counterpartName;
    private LocalDateTime matchedAt;
    private Float compatibilityPercentage;
    private String compatibilityLevel;
}