package ieti.jobswipe.exception;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.exception.ApiValidationExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiValidationExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestValidationController())
                .setControllerAdvice(new ApiValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnBadRequestBodyWhenValidationFails() throws Exception {
        mockMvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datos invalidos en la solicitud"))
                .andExpect(jsonPath("$.errors.name").value("name is required"));
    }

    @Test
    void shouldKeepFirstErrorPerField() {
        ApiValidationExceptionHandler handler = new ApiValidationExceptionHandler();
        assertEquals(ApiValidationExceptionHandler.class, handler.getClass());
    }

    @RestController
    @Validated
    static class TestValidationController {
        @PostMapping("/validation")
        ResponseEntity<TestRequest> validate(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.ok(request);
        }
    }

    static class TestRequest {
        @NotBlank(message = "name is required")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

