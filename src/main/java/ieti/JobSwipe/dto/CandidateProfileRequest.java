package ieti.JobSwipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidateProfileRequest {

    @NotBlank
    private String professionalTitle;

    @NotBlank
    private String summary;

    private String skills;

    private String experience;

    private String education;

    private String location;

    private String phoneNumber;

    private String languages;

    private Double expectedSalary;

    private String availability;

    private String portfolioUrl;

    private String cvUrl;
}
