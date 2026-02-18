package ieti.JobSwipe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.model.Vacancy;
import ieti.JobSwipe.repository.UserRepository;
import ieti.JobSwipe.repository.VacancyRepository;
import ieti.JobSwipe.service.VacancyService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacancyServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VacancyService vacancyService;

    private User testCompany;
    private Vacancy testVacancy;

    @BeforeEach
    void setUp() {
        testCompany = User.builder()
                .id(1L)
                .name("Tech Company")
                .email("company@example.com")
                .password("password123")
                .role(Role.COMPANY)
                .build();

        testVacancy = Vacancy.builder()
                .id(1L)
                .title("Senior Developer")
                .description("Looking for a senior Java developer")
                .salary(80000.0)
                .createdAt(LocalDateTime.now())
                .company(testCompany)
                .build();
    }

    @Test
    void shouldCreateVacancySuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(vacancyRepository.save(any(Vacancy.class))).thenReturn(testVacancy);

        Vacancy createdVacancy = vacancyService.createVacancy(testVacancy, 1L);

        assertNotNull(createdVacancy);
        assertEquals("Senior Developer", createdVacancy.getTitle());
        assertEquals(1L, createdVacancy.getCompany().getId());
        verify(userRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).save(any(Vacancy.class));
    }

    @Test
    void shouldThrowExceptionWhenCompanyNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            vacancyService.createVacancy(testVacancy, 999L);
        });

        assertEquals("Company not found", exception.getMessage());
        verify(userRepository, times(1)).findById(999L);
        verify(vacancyRepository, times(0)).save(any(Vacancy.class));
    }

    @Test
    void shouldReturnVacancyById() {
        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));

        Vacancy foundVacancy = vacancyService.getVacancyById(1L);

        assertNotNull(foundVacancy);
        assertEquals("Senior Developer", foundVacancy.getTitle());
        assertEquals(1L, foundVacancy.getId());
        verify(vacancyRepository, times(1)).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenVacancyNotFound() {
        when(vacancyRepository.findById(2L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            vacancyService.getVacancyById(2L);
        });

        assertEquals("Vacancy not found", exception.getMessage());
        verify(vacancyRepository, times(1)).findById(2L);
    }

    @Test
    void shouldReturnAllVacancies() {
        Vacancy another = Vacancy.builder()
                .id(2L)
                .title("Junior Developer")
                .description("Looking for a junior developer")
                .salary(40000.0)
                .createdAt(LocalDateTime.now())
                .company(testCompany)
                .build();

        when(vacancyRepository.findAll()).thenReturn(Arrays.asList(testVacancy, another));

        List<Vacancy> all = vacancyService.getAllVacancies();

        assertNotNull(all);
        assertEquals(2, all.size());
        verify(vacancyRepository, times(1)).findAll();
    }

    @Test
    void shouldUpdateVacancy() {
        Vacancy updates = Vacancy.builder()
                .title("Lead Developer")
                .description("Lead role")
                .salary(90000.0)
                .build();

        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));
        when(vacancyRepository.save(any(Vacancy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vacancy updated = vacancyService.updateVacancy(1L, updates);

        assertNotNull(updated);
        assertEquals("Lead Developer", updated.getTitle());
        assertEquals("Lead role", updated.getDescription());
        assertEquals(90000.0, updated.getSalary());
        verify(vacancyRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).save(any(Vacancy.class));
    }

    @Test
    void shouldDeleteVacancy() {
        when(vacancyRepository.findById(1L)).thenReturn(Optional.of(testVacancy));

        vacancyService.deleteVacancy(1L);

        verify(vacancyRepository, times(1)).findById(1L);
        verify(vacancyRepository, times(1)).delete(testVacancy);
    }
}
