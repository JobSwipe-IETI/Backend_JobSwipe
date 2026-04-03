package ieti.JobSwipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyProfileRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    private String companyDescription;

    private String legalId;

    private String industry;

    private String companySize;

    private String website;

    private String headquartersLocation;

    private String phoneNumber;

    private String hiringContactName;

    private String hiringContactEmail;
}
