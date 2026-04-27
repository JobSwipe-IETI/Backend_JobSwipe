package ieti.jobswipe.controller.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import ieti.jobswipe.controller.auth.AuthController;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.repository.profile.ProfileRepository;
import ieti.jobswipe.security.AuthenticatedUser;
import ieti.jobswipe.security.IdentityTokenVerifier;
import ieti.jobswipe.security.InvalidIdentityTokenException;
import ieti.jobswipe.security.JwtTokenService;
import ieti.jobswipe.security.UserProvisioningService;
import ieti.jobswipe.service.user.UserService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

        @Mock
        private IdentityTokenVerifier identityTokenVerifier;

        @Mock
        private UserProvisioningService userProvisioningService;

        @Mock
        private JwtTokenService jwtTokenService;

        @Mock
        private UserService userService;

        @Mock
        private ProfileRepository profileRepository;

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
                when(profileRepository.existsByUserId(1L)).thenReturn(false);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", is("jwt-token-123")))
                                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                                .andExpect(jsonPath("$.expiresIn", is(3600)))
                                .andExpect(jsonPath("$.hasProfile", is(false)))
                                .andExpect(jsonPath("$.user.id", is(1)))
                                .andExpect(jsonPath("$.user.email", is("john@example.com")))
                                .andExpect(jsonPath("$.user.name", is("John Doe")))
                                .andExpect(jsonPath("$.user.role", is("CANDIDATE")));

                verify(identityTokenVerifier, times(1)).verify(validIdToken);
                verify(userProvisioningService, times(1)).ensureUserExists(testAuthenticatedUser);
                verify(jwtTokenService, times(1)).generateToken(testUser);
                verify(profileRepository, times(1)).existsByUserId(1L);
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
        void shouldThrowBadRequestWhenIdTokenIsBlank() throws Exception {
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest("   ", null);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldThrowBadRequestWhenGoogleRequestIsNull() {
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> authController.authenticateWithGoogle(null));

                assertEquals(400, exception.getStatusCode().value());
                assertEquals("400 BAD_REQUEST \"Google idToken is required\"", exception.getMessage());
        }

        @Test
        void shouldCreateNewUserOnFirstLogin() throws Exception {
                String validIdToken = "valid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(validIdToken, null);

                when(identityTokenVerifier.verify(validIdToken)).thenReturn(testAuthenticatedUser);
                when(userProvisioningService.ensureUserExists(testAuthenticatedUser)).thenReturn(testUser);
                when(jwtTokenService.generateToken(testUser)).thenReturn(testTokenPayload);
                when(profileRepository.existsByUserId(1L)).thenReturn(false);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                verify(userProvisioningService, times(1)).ensureUserExists(any(AuthenticatedUser.class));
                verify(jwtTokenService, times(1)).generateToken(testUser);
                verify(profileRepository, times(1)).existsByUserId(1L);
        }

        @Test
        void shouldReturnUserResponseWithAllFields() throws Exception {
                String validIdToken = "valid.google.token";
                AuthController.GoogleAuthRequest request = new AuthController.GoogleAuthRequest(validIdToken, null);

                when(identityTokenVerifier.verify(validIdToken)).thenReturn(testAuthenticatedUser);
                when(userProvisioningService.ensureUserExists(testAuthenticatedUser)).thenReturn(testUser);
                when(jwtTokenService.generateToken(testUser)).thenReturn(testTokenPayload);
                when(profileRepository.existsByUserId(1L)).thenReturn(true);

                mockMvc.perform(post("/api/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.hasProfile", is(true)))
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

        @Test
        void shouldUpdateAuthenticatedUserRole() {
                User updatedUser = User.builder()
                                .id(1L)
                                .name("John Doe")
                                .email("john@example.com")
                                .googleId("google-subject-123")
                                .avatarUrl("https://example.com/avatar.jpg")
                                .role(Role.COMPANY)
                                .build();
                AuthController.RoleUpdateRequest request = new AuthController.RoleUpdateRequest("COMPANY");

                when(userService.updateUserRole(1L, Role.COMPANY)).thenReturn(updatedUser);
                when(jwtTokenService.generateToken(updatedUser)).thenReturn(testTokenPayload);
                when(profileRepository.existsByUserId(1L)).thenReturn(false);

                AuthController.AuthTokenResponse response = authController
                                .updateMyRole(request, jwt("1", "john@example.com", "John Doe"))
                                .getBody();

                assertEquals("COMPANY", response.user().role());
                assertEquals("jwt-token-123", response.accessToken());
                verify(userService, times(1)).updateUserRole(1L, Role.COMPANY);
                verify(jwtTokenService, times(1)).generateToken(updatedUser);
        }

        @Test
        void shouldRejectInvalidRoleUpdate() {
                AuthController.RoleUpdateRequest request = new AuthController.RoleUpdateRequest("INVALID");

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> authController.updateMyRole(request, jwt("1", "john@example.com", "John Doe")));

                assertEquals(400, exception.getStatusCode().value());
        }

        @Test
        void shouldReturnAuthenticatedUserClaims() {
                AuthController.AuthenticatedUserResponse response = authController
                                .getAuthenticatedUser(jwt("1", "john@example.com", "John Doe"))
                                .getBody();

                assertEquals("john@example.com", response.email());
                assertEquals("John Doe", response.name());
        }

        private Jwt jwt(String subject, String email, String name) {
                return Jwt.withTokenValue("jwt-token")
                                .header("alg", "HS256")
                                .subject(subject)
                                .claim("email", email)
                                .claim("name", name)
                                .build();
        }
}

