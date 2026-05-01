package ieti.jobswipe.controller.matching;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.dto.matching.MatchingRequest;
import ieti.jobswipe.dto.matching.MatchingResponse;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.service.matching.MatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MatchingControllerTest {

    @Mock
    private MatchingService matchingService;

        @Mock
        private UserRepository userRepository;

    @InjectMocks
    private MatchingController matchingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(matchingController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCalculateMatchingSuccessfully() throws Exception {
        MatchingRequest request = MatchingRequest.builder()
                .userId(1L)
                .vacancyId(10L)
                .build();

        User premiumUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();

        MatchingResponse response = MatchingResponse.builder()
                .similarityScore(0.91)
                .compatibilityPercentage(88.0f)
                .compatibilityLevel("high")
                .feedback("Great fit")
                .usedLlmFeedback(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(premiumUser));
        when(matchingService.calculateMatch(1L, 10L)).thenReturn(response);

        mockMvc.perform(post("/matching/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityScore", is(0.91)))
                .andExpect(jsonPath("$.compatibilityPercentage", is(88.0)))
                .andExpect(jsonPath("$.compatibilityLevel", is("high")))
                .andExpect(jsonPath("$.feedback", is("Great fit")))
                .andExpect(jsonPath("$.usedLlmFeedback", is(true)));

        verify(matchingService, times(1)).calculateMatch(1L, 10L);
    }

    @Test
    void shouldReturn404WhenResourceNotFound() throws Exception {
        MatchingRequest request = MatchingRequest.builder()
                .userId(1L)
                .vacancyId(999L)
                .build();

        User premiumUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(premiumUser));
        when(matchingService.calculateMatch(1L, 999L)).thenThrow(new RuntimeException("Profile not found"));

        mockMvc.perform(post("/matching/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(matchingService, times(1)).calculateMatch(1L, 999L);
    }

    @Test
    void shouldReturn503WhenAiServiceFails() throws Exception {
        MatchingRequest request = MatchingRequest.builder()
                .userId(1L)
                .vacancyId(10L)
                .build();

        User premiumUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CANDIDATE)
                .isPremium(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(premiumUser));
        when(matchingService.calculateMatch(1L, 10L)).thenThrow(new RuntimeException("AI service unavailable"));

        mockMvc.perform(post("/matching/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());

        verify(matchingService, times(1)).calculateMatch(1L, 10L);
    }

    @Test
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        String invalidRequestJson = "{\"vacancyId\":10}";

        mockMvc.perform(post("/matching/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest());
    }
}

