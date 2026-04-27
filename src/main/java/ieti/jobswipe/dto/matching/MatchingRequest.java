package ieti.jobswipe.dto.matching;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchingRequest {
    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "vacancyId is required")
    private Long vacancyId;
}

