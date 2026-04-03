package ieti.JobSwipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.JobSwipe.controller.VacancyController;
import ieti.JobSwipe.dto.CreateExtendedVacancyRequest;
import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.service.VacancyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VacancyControllerTest {

    @Mock
    private VacancyService vacancyService;

    @InjectMocks
    private VacancyController vacancyController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private User testCompany;
    private Vacancy testVacancy;
    private Vacancy testVacancy2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vacancyController).build();
        objectMapper = new ObjectMapper();

        testCompany = User.builder()
                .id(1L)
                .name("Tech Company Inc")
                .email("company@tech.com")
                .password("companypass123")
                .role(Role.COMPANY)
                .build();

        testVacancy = Vacancy.builder()
                .id(1L)
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .salary(5000.0)
                .company(testCompany)
                .build();

        testVacancy2 = Vacancy.builder()
                .id(2L)
                .title("Frontend Developer")
                .description("React + TypeScript")
                .salary(4500.0)
                .company(testCompany)
                .build();
    }


    @Test
    void shouldGetAllVacancies() throws Exception {
        // Arrange
        List<Vacancy> vacancies = Arrays.asList(testVacancy, testVacancy2);
        when(vacancyService.getAllVacancies()).thenReturn(vacancies);

        mockMvc.perform(get("/vacancies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("Backend Developer")))
                .andExpect(jsonPath("$[0].description", is("Java + Spring Boot")))
                .andExpect(jsonPath("$[0].salary", is(5000.0)))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].title", is("Frontend Developer")))
                .andExpect(jsonPath("$[1].description", is("React + TypeScript")))
                .andExpect(jsonPath("$[1].salary", is(4500.0)));

        verify(vacancyService, times(1)).getAllVacancies();
    }


    @Test
    void shouldGetVacancyById() throws Exception {
        when(vacancyService.getVacancyById(1L)).thenReturn(testVacancy);

        mockMvc.perform(get("/vacancies/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Backend Developer")))
                .andExpect(jsonPath("$.description", is("Java + Spring Boot")))
                .andExpect(jsonPath("$.salary", is(5000.0)));

        verify(vacancyService, times(1)).getVacancyById(1L);
    }

    @Test
    void shouldReturn404WhenVacancyNotFound() throws Exception {
        when(vacancyService.getVacancyById(999L))
                .thenThrow(new RuntimeException("Vacancy not found"));

        mockMvc.perform(get("/vacancies/{id}", 999L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).getVacancyById(999L);
    }


    @Test
    void shouldCreateVacancy() throws Exception {
        Vacancy vacancyRequest = Vacancy.builder()
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .salary(5000.0)
                .build();

        when(vacancyService.createVacancy(any(Vacancy.class), eq(1L)))
                .thenReturn(testVacancy);

        String vacancyJson = objectMapper.writeValueAsString(vacancyRequest);

        mockMvc.perform(post("/vacancies")
                .param("companyId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(vacancyJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Backend Developer")))
                .andExpect(jsonPath("$.description", is("Java + Spring Boot")))
                .andExpect(jsonPath("$.salary", is(5000.0)));

        verify(vacancyService, times(1)).createVacancy(any(Vacancy.class), eq(1L));
    }

    @Test
    void shouldReturn404WhenCompanyNotFound() throws Exception {
        Vacancy vacancyRequest = Vacancy.builder()
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .salary(5000.0)
                .build();

        when(vacancyService.createVacancy(any(Vacancy.class), eq(999L)))
                .thenThrow(new RuntimeException("Company not found"));

        String vacancyJson = objectMapper.writeValueAsString(vacancyRequest);

        mockMvc.perform(post("/vacancies")
                .param("companyId", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(vacancyJson))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).createVacancy(any(Vacancy.class), eq(999L));
    }

    @Test
    void shouldUpdateVacancy() throws Exception {
        Vacancy vacancyUpdate = Vacancy.builder()
                .title("Senior Backend Developer")
                .description("Java + Spring Boot + Kubernetes")
                .salary(6500.0)
                .build();

        Vacancy updatedVacancy = Vacancy.builder()
                .id(1L)
                .title("Senior Backend Developer")
                .description("Java + Spring Boot + Kubernetes")
                .salary(6500.0)
                .company(testCompany)
                .build();

        when(vacancyService.updateVacancy(eq(1L), any(Vacancy.class)))
                .thenReturn(updatedVacancy);

        String vacancyJson = objectMapper.writeValueAsString(vacancyUpdate);

        mockMvc.perform(put("/vacancies/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(vacancyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Senior Backend Developer")))
                .andExpect(jsonPath("$.description", is("Java + Spring Boot + Kubernetes")))
                .andExpect(jsonPath("$.salary", is(6500.0)));

        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(Vacancy.class));
    }

    @Test
    void shouldReturn404WhenUpdatingVacancy() throws Exception {
        Vacancy vacancyUpdate = Vacancy.builder()
                .title("Senior Backend Developer")
                .description("Java + Spring Boot + Kubernetes")
                .salary(6500.0)
                .build();

        when(vacancyService.updateVacancy(eq(999L), any(Vacancy.class)))
                .thenThrow(new RuntimeException("Vacancy not found"));

        String vacancyJson = objectMapper.writeValueAsString(vacancyUpdate);

        mockMvc.perform(put("/vacancies/{id}", 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(vacancyJson))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).updateVacancy(eq(999L), any(Vacancy.class));
    }

    @Test
    void shouldDeleteVacancy() throws Exception {
        doNothing().when(vacancyService).deleteVacancy(1L);

        mockMvc.perform(delete("/vacancies/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(vacancyService, times(1)).deleteVacancy(1L);
    }

    @Test
    void shouldCreateExtendedVacancyWithMultipartPayload() throws Exception {
        CreateExtendedVacancyRequest request = new CreateExtendedVacancyRequest();
        request.setPositionRequested("Backend Developer");
        request.setVacancySummary("Vacancy summary");
        request.setDesiredMonthlySalary(5000.0);
        request.setApplicationDate("2026-04-02");
        request.setCandidateFullName("John Doe");
        request.setPhoneNumber("3000000000");
        request.setEmail("john@example.com");
        request.setPermanentAddress("Main Street 123");
        request.setBirthDate("1995-01-01");
        request.setOfficialIdentification("ID-12345");
        request.setAcademicLevel("Professional");
        request.setInstitutionDetails("University");
        request.setPreviousEmploymentData("2 years");
        request.setResponsibilities("Backend services");

        MockMultipartFile payloadPart = new MockMultipartFile(
                "payload",
                "payload.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        MockMultipartFile documentPart = new MockMultipartFile(
                "document",
                "cv.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake-pdf-content".getBytes());

        when(vacancyService.createExtendedVacancy(any(CreateExtendedVacancyRequest.class), eq(1L), any()))
                .thenReturn(testVacancy);

        mockMvc.perform(multipart("/vacancies/extended/multipart")
                .file(payloadPart)
                .file(documentPart)
                .param("companyId", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Backend Developer")));

        verify(vacancyService, times(1)).createExtendedVacancy(any(CreateExtendedVacancyRequest.class), eq(1L), any());
    }

    @Test
    void shouldReturn400WhenExtendedPayloadIsInvalidJson() throws Exception {
        MockMultipartFile payloadPart = new MockMultipartFile(
                "payload",
                "payload.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{ invalid json }".getBytes());

        mockMvc.perform(multipart("/vacancies/extended/multipart")
                .file(payloadPart)
                .param("companyId", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenDeletingVacancy() throws Exception {
        doThrow(new RuntimeException("Vacancy not found"))
                .when(vacancyService).deleteVacancy(999L);

        mockMvc.perform(delete("/vacancies/{id}", 999L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).deleteVacancy(999L);
    }
}
