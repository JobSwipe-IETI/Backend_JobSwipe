package ieti.jobswipe.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidateProfileRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
    private String displayName;

    @NotBlank(message = "El titulo profesional es obligatorio")
    @Size(min = 3, max = 120, message = "El titulo profesional debe tener entre 3 y 120 caracteres")
    private String professionalTitle;

    @NotBlank(message = "El resumen es obligatorio")
    @Size(min = 30, max = 1500, message = "El resumen debe tener entre 30 y 1500 caracteres")
    private String summary;

    @Size(max = 30, message = "Puedes registrar maximo 30 habilidades")
    @Valid
    private List<String> skills;

    @Size(max = 20, message = "Puedes registrar maximo 20 experiencias")
    @Valid
    private List<CandidateExperienceRequest> experiences;

    @Size(max = 300, message = "La educacion no puede superar 300 caracteres")
    private String education;

    @Size(max = 120, message = "La ubicacion no puede superar 120 caracteres")
    private String location;

    @NotBlank(message = "La nacionalidad es obligatoria")
    @Size(max = 80, message = "La nacionalidad no puede superar 80 caracteres")
    private String nationality;

    @Pattern(
        regexp = "^$|^(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(?\\d{2,4}\\)?[\\s-]?)?\\d{3,4}[\\s-]?\\d{3,4}$",
        message = "Telefono invalido")
    private String phoneNumber;

    @Size(max = 200, message = "Idiomas no puede superar 200 caracteres")
    private String languages;

    @PositiveOrZero(message = "El salario esperado no puede ser negativo")
    private Double expectedSalary;

    @Size(max = 120, message = "Disponibilidad no puede superar 120 caracteres")
    private String availability;

    @Size(max = 80, message = "El sector no puede superar 80 caracteres")
    private String sector;

    @Pattern(
        regexp = "^$|^(https?://).+",
        message = "El GitHub debe ser una URL valida que inicie con http:// o https://")
    private String githubUrl;

    @Pattern(
        regexp = "^$|^(https?://).+",
        message = "El LinkedIn debe ser una URL valida que inicie con http:// o https://")
    private String linkedinUrl;

    @Pattern(
        regexp = "^$|^(https?://).+",
        message = "El portafolio debe ser una URL valida que inicie con http:// o https://")
    private String portfolioUrl;
}

