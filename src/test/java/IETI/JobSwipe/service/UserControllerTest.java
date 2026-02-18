package IETI.JobSwipe.service;

import IETI.JobSwipe.controller.UserController;
import IETI.JobSwipe.model.Role;
import IETI.JobSwipe.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .build();
    }

    @Test
    void shouldGetAllUsers() throws Exception {
        User user1 = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .role(Role.CANDIDATE)
                .build();

        User user2 = User.builder()
                .id(2L)
                .name("Alice Smith")
                .email("alice@example.com")
                .password("password456")
                .role(Role.COMPANY)
                .build();

        List<User> users = Arrays.asList(user1, user2);
        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("John Doe")))
                .andExpect(jsonPath("$[0].email", is("john@example.com")))
                .andExpect(jsonPath("$[0].role", is("CANDIDATE")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].name", is("Alice Smith")))
                .andExpect(jsonPath("$[1].email", is("alice@example.com")))
                .andExpect(jsonPath("$[1].role", is("COMPANY")));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void shouldGetUserById() throws Exception {
        when(userService.getUserById(1L)).thenReturn(testUser);

        mockMvc.perform(get("/users/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.password", is("password123")))
                .andExpect(jsonPath("$.role", is("CANDIDATE")));

        verify(userService, times(1)).getUserById(1L);
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/users/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).getUserById(999L);
    }

    @Test
    void shouldCreateUser() throws Exception {
        User userRequest = User.builder()
                .name("Jane Smith")
                .email("jane@example.com")
                .password("newpassword")
                .role(Role.COMPANY)
                .build();

        User createdUser = User.builder()
                .id(2L)
                .name("Jane Smith")
                .email("jane@example.com")
                .password("newpassword")
                .role(Role.COMPANY)
                .build();

        when(userService.createUser(any(User.class))).thenReturn(createdUser);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.name", is("Jane Smith")))
                .andExpect(jsonPath("$.email", is("jane@example.com")))
                .andExpect(jsonPath("$.password", is("newpassword")))
                .andExpect(jsonPath("$.role", is("COMPANY")));

        verify(userService, times(1)).createUser(any(User.class));
    }

    @Test
    void shouldUpdateUser() throws Exception {
        User updateRequest = User.builder()
                .name("John Updated")
                .email("john.updated@example.com")
                .password("newpassword123")
                .role(Role.COMPANY)
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .name("John Updated")
                .email("john.updated@example.com")
                .password("newpassword123")
                .role(Role.COMPANY)
                .build();

        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("John Updated")))
                .andExpect(jsonPath("$.email", is("john.updated@example.com")))
                .andExpect(jsonPath("$.password", is("newpassword123")))
                .andExpect(jsonPath("$.role", is("COMPANY")));

        verify(userService, times(1)).updateUser(eq(1L), any(User.class));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingUser() throws Exception {
        User updateRequest = User.builder()
                .name("Someone")
                .email("someone@example.com")
                .password("password")
                .role(Role.CANDIDATE)
                .build();

        when(userService.updateUser(eq(999L), any(User.class)))
                .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(put("/users/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).updateUser(eq(999L), any(User.class));
    }

    @Test
    void shouldDeleteUser() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/users/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(1L);
    }

    @Test
    void shouldReturn404WhenDeletingUser() throws Exception {
        doThrow(new RuntimeException("User not found"))
                .when(userService).deleteUser(999L);

        mockMvc.perform(delete("/users/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).deleteUser(999L);
    }
}
