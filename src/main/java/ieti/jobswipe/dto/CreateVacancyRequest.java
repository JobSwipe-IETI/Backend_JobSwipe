package ieti.jobswipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateVacancyRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String location;

    @NotNull
    private String modality;

    @NotNull
    private String employmentType;

    @NotNull
    private String experienceLevel;

    private String sector;

    private List<String> technologies;

    private List<String> softSkills;

    private List<String> responsibilities;

    private List<String> technicalRequirements;

    @NotNull
    @PositiveOrZero
    private Double minSalary;

    @NotNull
    @PositiveOrZero
    private Double maxSalary;

    private List<String> benefits;
}

