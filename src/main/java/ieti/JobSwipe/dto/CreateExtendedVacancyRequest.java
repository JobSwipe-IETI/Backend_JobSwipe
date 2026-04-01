package ieti.JobSwipe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateExtendedVacancyRequest {

    @NotBlank
    private String positionRequested;

    @NotBlank
    private String vacancySummary;

    @NotNull
    @PositiveOrZero
    private Double desiredMonthlySalary;

    @NotBlank
    private String applicationDate;

    @NotBlank
    private String candidateFullName;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String permanentAddress;

    @NotBlank
    private String birthDate;

    @NotBlank
    private String officialIdentification;

    private String otherDocuments;

    @NotBlank
    private String academicLevel;

    @NotBlank
    private String institutionDetails;

    @NotBlank
    private String previousEmploymentData;

    @NotBlank
    private String responsibilities;

    private String salaryHistory;

    private String languages;

    private String officeFunctions;

    private String softwareAndMachinery;

    private String softSkills;

    private String personalAndWorkReferences;

    private String referencesContactInfo;

    private String openQuestionsAnswers;

    private String killerQuestionsAnswers;

    private String documentAttachments;

    private String habitsAndGoals;
}
