package ieti.JobSwipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.JobSwipe.dto.CreateVacancyRequest;
import ieti.JobSwipe.model.EmploymentType;
import ieti.JobSwipe.model.ExperienceLevel;
import ieti.JobSwipe.model.Modality;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    private CreateVacancyRequest testRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vacancyController)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        objectMapper = new ObjectMapper();

        testCompany = User.builder()
                .id(1L)
                .name("Tech Company Inc")
                .email("company@tech.com")
                .role(Role.COMPANY)
                .build();

        testVacancy = Vacancy.builder()
                .id(1L)
                .title("Backend Developer")
                .description("Java + Spring Boot")
                .location("Bogotá, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(5000.0)
                .maxSalary(8000.0)
                .company(testCompany)
                .build();

        testVacancy2 = Vacancy.builder()
                .id(2L)
                .title("Frontend Developer")
                .description("React + TypeScript")
                .location("Medellín, Colombia")
                .modality(Modality.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SEMI_SENIOR)
                .minSalary(4000.0)
                .maxSalary(6000.0)
                .company(testCompany)
                .build();

        testRequest = new CreateVacancyRequest();
        testRequest.setTitle("Backend Developer");
        testRequest.setDescription("Java + Spring Boot");
        testRequest.setLocation("Bogotá, Colombia");
        testRequest.setModality("REMOTE");
        testRequest.setEmploymentType("FULL_TIME");
        testRequest.setExperienceLevel("SENIOR");
        testRequest.setMinSalary(5000.0);
        testRequest.setMaxSalary(8000.0);
    }

    @Test
    void shouldGetAllVacancies() throws Exception {
        List<Vacancy> vacancies = Arrays.asList(testVacancy, testVacancy2);
        when(vacancyService.getAllVacancies()).thenReturn(vacancies);

        mockMvc.perform(get("/vacancies")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("Backend Developer")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].title", is("Frontend Developer")));

        verify(vacancyService, times(1)).getAllVacancies();
    }

    @Test
    void shouldGetVacancyById() throws Exception {
        when(vacancyService.getVacancyById(1L)).thenReturn(testVacancy);

        mockMvc.perform(get("/vacancies/{id}", 1L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Backend Developer")))
                .andExpect(jsonPath("$.minSalary", is(5000.0)))
                .andExpect(jsonPath("$.maxSalary", is(8000.0)));

        verify(vacancyService, times(1)).getVacancyById(1L);
    }

    @Test
    void shouldReturn404WhenVacancyNotFound() throws Exception {
        when(vacancyService.getVacancyById(999L))
                .thenThrow(new RuntimeException("Vacancy not found"));

        mockMvc.perform(get("/vacancies/{id}", 999L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).getVacancyById(999L);
    }

    @Test
    void shouldCreateVacancy() throws Exception {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L)))
                .thenReturn(testVacancy);

        mockMvc.perform(post("/vacancies")
                .with(jwt().jwt(j -> j.subject("1").claim("role", "COMPANY")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Backend Developer")))
                .andExpect(jsonPath("$.minSalary", is(5000.0)))
                .andExpect(jsonPath("$.maxSalary", is(8000.0)));

        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(1L));
    }

    @Test
    void shouldReturn404WhenCompanyNotFound() throws Exception {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(999L)))
                .thenThrow(new RuntimeException("Company not found"));

        mockMvc.perform(post("/vacancies")
                .with(jwt().jwt(j -> j.subject("999").claim("role", "COMPANY")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).createVacancy(any(CreateVacancyRequest.class), eq(999L));
    }

    @Test
    void shouldReturn400WhenRoleIsNotCompany() throws Exception {
        when(vacancyService.createVacancy(any(CreateVacancyRequest.class), eq(1L)))
                .thenThrow(new IllegalArgumentException("Only COMPANY users can create vacancies"));

        mockMvc.perform(post("/vacancies")
                .with(jwt().jwt(j -> j.subject("1").claim("role", "CANDIDATE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateVacancy() throws Exception {
        CreateVacancyRequest updateRequest = new CreateVacancyRequest();
        updateRequest.setTitle("Senior Backend Developer");
        updateRequest.setDescription("Java + Spring Boot + Kubernetes");
        updateRequest.setLocation("Bogotá, Colombia");
        updateRequest.setModality("REMOTE");
        updateRequest.setEmploymentType("FULL_TIME");
        updateRequest.setExperienceLevel("SENIOR");
        updateRequest.setMinSalary(6000.0);
        updateRequest.setMaxSalary(9000.0);

        Vacancy updatedVacancy = Vacancy.builder()
                .id(1L)
                .title("Senior Backend Developer")
                .description("Java + Spring Boot + Kubernetes")
                .location("Bogotá, Colombia")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minSalary(6000.0)
                .maxSalary(9000.0)
                .company(testCompany)
                .build();

        when(vacancyService.updateVacancy(eq(1L), any(CreateVacancyRequest.class)))
                .thenReturn(updatedVacancy);

        mockMvc.perform(put("/vacancies/{id}", 1L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Senior Backend Developer")))
                .andExpect(jsonPath("$.minSalary", is(6000.0)))
                .andExpect(jsonPath("$.maxSalary", is(9000.0)));

        verify(vacancyService, times(1)).updateVacancy(eq(1L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldReturn404WhenUpdatingVacancyNotFound() throws Exception {
        when(vacancyService.updateVacancy(eq(999L), any(CreateVacancyRequest.class)))
                .thenThrow(new RuntimeException("Vacancy not found"));

        mockMvc.perform(put("/vacancies/{id}", 999L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).updateVacancy(eq(999L), any(CreateVacancyRequest.class));
    }

    @Test
    void shouldDeleteVacancy() throws Exception {
        doNothing().when(vacancyService).deleteVacancy(1L);

        mockMvc.perform(delete("/vacancies/{id}", 1L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(vacancyService, times(1)).deleteVacancy(1L);
    }

    @Test
    void shouldReturn404WhenDeletingVacancyNotFound() throws Exception {
        doThrow(new RuntimeException("Vacancy not found"))
                .when(vacancyService).deleteVacancy(999L);

        mockMvc.perform(delete("/vacancies/{id}", 999L)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(vacancyService, times(1)).deleteVacancy(999L);
    }
}
