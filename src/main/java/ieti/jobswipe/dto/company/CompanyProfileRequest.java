package ieti.jobswipe.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyProfileRequest {

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    @Size(min = 2, max = 120, message = "El nombre de la empresa debe tener entre 2 y 120 caracteres")
    private String companyName;

    @NotBlank(message = "La descripcion de la empresa es obligatoria")
    @Size(min = 30, max = 2000, message = "La descripcion debe tener entre 30 y 2000 caracteres")
    private String companyDescription;

    @Size(max = 80, message = "El NIT/ID legal no puede superar 80 caracteres")
    private String legalId;

    @Size(max = 120, message = "La industria no puede superar 120 caracteres")
    private String industry;

    @Size(max = 80, message = "El tamano de empresa no puede superar 80 caracteres")
    private String companySize;

    @Pattern(
            regexp = "^$|^(https?://).+",
            message = "El sitio web debe ser una URL valida que inicie con http:// o https://")
    private String website;

    @Size(max = 150, message = "La ubicacion de la sede no puede superar 150 caracteres")
    private String headquartersLocation;

        @NotBlank(message = "El pais/nacionalidad es obligatorio")
        @Size(max = 80, message = "El pais/nacionalidad no puede superar 80 caracteres")
        private String nationality;

        @Pattern(
            regexp = "^$|^(\\+\\d{1,3}[\\s-]?)?\\d{6,15}$",
            message = "Telefono invalido")
    private String phoneNumber;

    @Size(max = 120, message = "El nombre del contacto no puede superar 120 caracteres")
    private String hiringContactName;

    @Email(message = "Email de contacto invalido")
    @Size(max = 160, message = "El email de contacto no puede superar 160 caracteres")
    private String hiringContactEmail;
}

