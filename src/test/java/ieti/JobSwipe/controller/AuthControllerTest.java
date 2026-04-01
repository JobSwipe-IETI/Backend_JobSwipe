package ieti.JobSwipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.JobSwipe.model.Role;
import ieti.JobSwipe.model.User;
import ieti.JobSwipe.security.AuthenticatedUser;
import ieti.JobSwipe.security.IdentityTokenVerifier;
import ieti.JobSwipe.security.InvalidIdentityTokenException;
import ieti.JobSwipe.security.JwtTokenService;
import ieti.JobSwipe.security.UserProvisioningService;

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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

        @Mock
        private IdentityTokenVerifier identityTokenVerifier;

        @Mock
        private UserProvisioningService userProvisioningService;

        @Mock
        private JwtTokenService jwtTokenService;

        @InjectMocks
        private AuthController authController;

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;

        private AuthenticatedUser testAuthenticatedUser;
        private User testUser;
        private JwtTokenService.TokenPayload testTokenPayload;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
                objectMapper = new ObjectMapper();

                testAuthenticatedUser = new AuthenticatedUser(
                                "google-subject-123",
                                "john@example.com",
                                "John Doe",
                                "https://example.com/avatar.jpg");

                testUser = User.builder()
                                .id(1L)
                                .name("John Doe")
                                .email("john@example.com")
                                .googleId("google-subject-123")
                                .avatarUrl("https://example.com/avatar.jpg")
                                .password(null)
                                .role(Role.CANDIDATE)
                                .build();

                testTokenPayload = new JwtTokenService.TokenPayload("jwt-token-123", 3600);
        }

        @Test
        void shouldReturnJwtTokenWhenGoogleIdTokenIsValid() throws Exception {
                String validIdToken = "valid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(validIdToken, null);

                when(identityTokenVerifier.verify(validIdToken)).thenReturn(testAuthenticatedUser);
                when(userProvisioningService.ensureUserExists(testAuthenticatedUser)).thenReturn(testUser);
                when(jwtTokenService.generateToken(testUser)).thenReturn(testTokenPayload);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", is("jwt-token-123")))
                                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                                .andExpect(jsonPath("$.expiresIn", is(3600)))
                                .andExpect(jsonPath("$.user.id", is(1)))
                                .andExpect(jsonPath("$.user.email", is("john@example.com")))
                                .andExpect(jsonPath("$.user.name", is("John Doe")))
                                .andExpect(jsonPath("$.user.role", is("CANDIDATE")));

                verify(identityTokenVerifier, times(1)).verify(validIdToken);
                verify(userProvisioningService, times(1)).ensureUserExists(testAuthenticatedUser);
                verify(jwtTokenService, times(1)).generateToken(testUser);
        }

        @Test
        void shouldThrowBadRequestWhenIdTokenIsNull() throws Exception {
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(null, null);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldThrowBadRequestWhenIdTokenIsEmpty() throws Exception {
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest("", null);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldCreateNewUserOnFirstLogin() throws Exception {
                String validIdToken = "valid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(validIdToken, null);

                when(identityTokenVerifier.verify(validIdToken)).thenReturn(testAuthenticatedUser);
                when(userProvisioningService.ensureUserExists(testAuthenticatedUser)).thenReturn(testUser);
                when(jwtTokenService.generateToken(testUser)).thenReturn(testTokenPayload);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                verify(userProvisioningService, times(1)).ensureUserExists(any(AuthenticatedUser.class));
                verify(jwtTokenService, times(1)).generateToken(testUser);
        }

        @Test
        void shouldReturnUserResponseWithAllFields() throws Exception {
                String validIdToken = "valid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(validIdToken, null);

                when(identityTokenVerifier.verify(validIdToken)).thenReturn(testAuthenticatedUser);
                when(userProvisioningService.ensureUserExists(testAuthenticatedUser)).thenReturn(testUser);
                when(jwtTokenService.generateToken(testUser)).thenReturn(testTokenPayload);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user.id", notNullValue()))
                                .andExpect(jsonPath("$.user.name", notNullValue()))
                                .andExpect(jsonPath("$.user.email", notNullValue()))
                                .andExpect(jsonPath("$.user.googleId", notNullValue()))
                                .andExpect(jsonPath("$.user.avatarUrl", notNullValue()))
                                .andExpect(jsonPath("$.user.role", notNullValue()));
        }

        @Test
        void shouldHandleInvalidIdentityTokenException() throws Exception {
                String invalidIdToken = "invalid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(invalidIdToken, null);

                when(identityTokenVerifier.verify(invalidIdToken))
                                .thenThrow(new InvalidIdentityTokenException("Invalid Google identity token"));

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error", is("Invalid Google identity token")));

                verify(identityTokenVerifier, times(1)).verify(invalidIdToken);
        }
}
