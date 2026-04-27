package ieti.jobswipe.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidateExperienceRequest {

    @NotBlank(message = "El cargo es obligatorio")
    @Size(max = 120, message = "El cargo no puede superar 120 caracteres")
    private String title;

    @NotBlank(message = "La empresa es obligatoria")
    @Size(max = 120, message = "La empresa no puede superar 120 caracteres")
    private String company;

    @NotBlank(message = "La fecha de inicio es obligatoria")
    @Size(max = 30, message = "La fecha de inicio no puede superar 30 caracteres")
    private String startDate;

    @Size(max = 30, message = "La fecha de fin no puede superar 30 caracteres")
    private String endDate;

    private Boolean current;
}

