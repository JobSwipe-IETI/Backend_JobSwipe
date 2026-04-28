package ieti.jobswipe.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import ieti.jobswipe.dto.chat.ChatMessageResponse;
import ieti.jobswipe.dto.chat.ConversationSummaryResponse;
import ieti.jobswipe.model.EmploymentType;
import ieti.jobswipe.model.ExperienceLevel;
import ieti.jobswipe.model.Modality;
import ieti.jobswipe.model.Role;
import ieti.jobswipe.model.SwipeDecisionType;
import ieti.jobswipe.model.entity.ChatConversation;
import ieti.jobswipe.model.entity.ChatMessage;
import ieti.jobswipe.model.entity.User;
import ieti.jobswipe.model.entity.Vacancy;
import ieti.jobswipe.repository.chat.ChatConversationRepository;
import ieti.jobswipe.repository.chat.ChatMessageRepository;
import ieti.jobswipe.repository.company.CompanyCandidateDecisionRepository;
import ieti.jobswipe.repository.projection.ConversationSummaryProjection;
import ieti.jobswipe.repository.user.UserRepository;
import ieti.jobswipe.repository.vacancy.VacancyRepository;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private CompanyCandidateDecisionRepository companyCandidateDecisionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private SupabaseChatRealtimePublisher supabaseChatRealtimePublisher;

    @InjectMocks
    private ChatService chatService;

    private User company;
    private User candidate;
    private Vacancy vacancy;

    @BeforeEach
    void setUp() {
        company = User.builder()
                .id(10L)
                .name("Acme")
                .email("acme@jobswipe.com")
                .role(Role.COMPANY)
                .build();

        candidate = User.builder()
                .id(20L)
                .name("Ana")
                .email("ana@jobswipe.com")
                .role(Role.CANDIDATE)
                .build();

        vacancy = Vacancy.builder()
                .id(99L)
                .title("Java Engineer")
                .description("desc")
                .location("Bogota")
                .modality(Modality.REMOTE)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.MID)
                .minSalary(1000.0)
                .maxSalary(2000.0)
                .company(company)
                .build();
    }

    @Test
    void shouldGetConversationsForCompanyAndMapFallbacks() {
        ConversationSummaryProjection row = org.mockito.Mockito.mock(ConversationSummaryProjection.class);
        when(row.getConversationId()).thenReturn(1L);
        when(row.getVacancyId()).thenReturn(99L);
        when(row.getVacancyTitle()).thenReturn(null);
        when(row.getCounterpartId()).thenReturn(20L);
        when(row.getCounterpartName()).thenReturn(" ");
        when(row.getCounterpartRole()).thenReturn("CANDIDATE");
        when(row.getInitiatedByUserId()).thenReturn(10L);
        when(row.getUnreadCount()).thenReturn(null);

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(chatConversationRepository.findCompanyConversationSummaries(10L, 100)).thenReturn(List.of(row));

        List<ConversationSummaryResponse> result = chatService.getConversationsForUser(10L, 200);

        assertEquals(1, result.size());
        assertEquals("Vacante", result.get(0).getVacancyTitle());
        assertEquals("Usuario", result.get(0).getCounterpartName());
        assertEquals(0, result.get(0).getUnreadCount());
    }

    @Test
    void shouldGetConversationsForCandidate() {
        ConversationSummaryProjection row = org.mockito.Mockito.mock(ConversationSummaryProjection.class);
        when(row.getConversationId()).thenReturn(3L);
        when(row.getVacancyTitle()).thenReturn("Backend");
        when(row.getCounterpartName()).thenReturn("Empresa");
        when(row.getUnreadCount()).thenReturn(2);

        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(chatConversationRepository.findCandidateConversationSummaries(20L, 1)).thenReturn(List.of(row));

        List<ConversationSummaryResponse> result = chatService.getConversationsForUser(20L, 0);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getConversationId());
        assertEquals(2, result.get(0).getUnreadCount());
    }

    @Test
    void shouldFailStartConversationWhenActorIsNotCompany() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));

        assertThrows(IllegalArgumentException.class, () -> chatService.startConversation(20L, 99L, 20L));
    }

    @Test
    void shouldFailStartConversationWhenCounterpartIsNotCandidate() {
        User anotherCompany = User.builder().id(30L).name("Other").email("other@x.com").role(Role.COMPANY).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(userRepository.findById(30L)).thenReturn(Optional.of(anotherCompany));

        assertThrows(IllegalArgumentException.class, () -> chatService.startConversation(10L, 99L, 30L));
    }

    @Test
    void shouldCreateConversationWhenNoExistingOne() {
        ChatConversation savedConversation = ChatConversation.builder()
                .id(7L)
                .vacancyId(99L)
                .companyId(10L)
                .candidateId(20L)
                .initiatedByUserId(10L)
                .candidateUnreadCount(0)
                .companyUnreadCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(99L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(10L, 20L, 99L,
                SwipeDecisionType.LIKE)).thenReturn(true);
        when(chatConversationRepository.findByVacancyIdAndCompanyIdAndCandidateId(99L, 10L, 20L))
                .thenReturn(Optional.empty());
        when(chatConversationRepository.save(any(ChatConversation.class))).thenReturn(savedConversation);

        ConversationSummaryResponse result = chatService.startConversation(10L, 99L, 20L);

        assertEquals(7L, result.getConversationId());
        verify(chatConversationRepository, times(1)).save(any(ChatConversation.class));
    }

    @Test
    void shouldReturnExistingConversationWithoutSavingNewOne() {
        ChatConversation existing = ChatConversation.builder()
                .id(8L)
                .vacancyId(99L)
                .companyId(10L)
                .candidateId(20L)
                .initiatedByUserId(10L)
                .candidateUnreadCount(0)
                .companyUnreadCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(vacancyRepository.findById(99L)).thenReturn(Optional.of(vacancy));
        when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(10L, 20L, 99L,
                SwipeDecisionType.LIKE)).thenReturn(true);
        when(chatConversationRepository.findByVacancyIdAndCompanyIdAndCandidateId(99L, 10L, 20L))
                .thenReturn(Optional.of(existing));

        ConversationSummaryResponse result = chatService.startConversation(10L, 99L, 20L);

        assertEquals(8L, result.getConversationId());
        verify(chatConversationRepository, never()).save(any(ChatConversation.class));
    }

    @Test
    void shouldGetConversationMessagesInChronologicalOrderAndMineFlag() {
        ChatConversation conversation = ChatConversation.builder()
                .id(50L)
                .companyId(10L)
                .candidateId(20L)
                .build();

        ChatMessage newest = ChatMessage.builder()
                .id(2L)
                .conversationId(50L)
                .senderId(10L)
                .senderRole(Role.COMPANY)
                .content("new")
                .createdAt(LocalDateTime.now())
                .build();

        ChatMessage oldest = ChatMessage.builder()
                .id(1L)
                .conversationId(50L)
                .senderId(20L)
                .senderRole(Role.CANDIDATE)
                .content("old")
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(chatConversationRepository.findAccessibleConversation(50L, 10L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(eq(50L), any(Pageable.class)))
                .thenReturn(List.of(newest, oldest));

        List<ChatMessageResponse> result = chatService.getConversationMessages(10L, 50L, 200);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertFalse(result.get(0).isMine());
        assertTrue(result.get(1).isMine());
    }

    @Test
    void shouldSendMessageFromCompanyAndIncrementCandidateUnread() {
        ChatConversation conversation = ChatConversation.builder()
                .id(70L)
                .companyId(10L)
                .candidateId(20L)
                .candidateUnreadCount(1)
                .companyUnreadCount(0)
                .build();

        ChatMessage savedMessage = ChatMessage.builder()
                .id(700L)
                .conversationId(70L)
                .senderId(10L)
                .senderRole(Role.COMPANY)
                .content("hello")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(chatConversationRepository.findAccessibleConversation(70L, 10L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        String longContent = "  " + "a".repeat(2200) + "  ";
        ChatMessageResponse response = chatService.sendMessage(10L, 70L, longContent, "cli-1");

        assertEquals(700L, response.getId());
        assertTrue(response.isMine());
        assertEquals("cli-1", response.getClientMessageId());

        ArgumentCaptor<ChatConversation> captor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).save(captor.capture());
        ChatConversation updated = captor.getValue();
        assertEquals(2, updated.getCandidateUnreadCount());
        assertNotNull(updated.getLastMessagePreview());
        assertTrue(updated.getLastMessagePreview().endsWith("..."));

        verify(supabaseChatRealtimePublisher).publishMessage(any(ChatMessageResponse.class));
    }

        @Test
        void shouldSendMessageFromCandidateAndIncrementCompanyUnreadWithShortPreview() {
                ChatConversation conversation = ChatConversation.builder()
                                .id(71L)
                                .companyId(10L)
                                .candidateId(20L)
                                .candidateUnreadCount(0)
                                .companyUnreadCount(2)
                                .build();

                ChatMessage savedMessage = ChatMessage.builder()
                                .id(701L)
                                .conversationId(71L)
                                .senderId(20L)
                                .senderRole(Role.CANDIDATE)
                                .content("hello")
                                .createdAt(LocalDateTime.now())
                                .build();

                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(chatConversationRepository.findAccessibleConversation(71L, 20L)).thenReturn(Optional.of(conversation));
                when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

                ChatMessageResponse response = chatService.sendMessage(20L, 71L, " hello ", null);

                assertEquals(701L, response.getId());
                assertEquals(3, conversation.getCompanyUnreadCount());
                assertEquals("hello", conversation.getLastMessagePreview());
        }

    @Test
    void shouldRejectBlankMessage() {
        ChatConversation conversation = ChatConversation.builder().id(70L).companyId(10L).candidateId(20L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(chatConversationRepository.findAccessibleConversation(70L, 10L)).thenReturn(Optional.of(conversation));

        assertThrows(IllegalArgumentException.class, () -> chatService.sendMessage(10L, 70L, "   ", null));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void shouldMarkAsReadForCandidateWhenUnreadExists() {
        ChatConversation conversation = ChatConversation.builder()
                .id(88L)
                .companyId(10L)
                .candidateId(20L)
                .candidateUnreadCount(4)
                .companyUnreadCount(0)
                .build();

        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(chatConversationRepository.findAccessibleConversation(88L, 20L)).thenReturn(Optional.of(conversation));

        chatService.markConversationAsRead(20L, 88L);

        assertEquals(0, conversation.getCandidateUnreadCount());
        verify(chatConversationRepository).save(conversation);
    }

        @Test
        void shouldMarkAsReadForCompanyWhenUnreadExists() {
                ChatConversation conversation = ChatConversation.builder()
                                .id(90L)
                                .companyId(10L)
                                .candidateId(20L)
                                .candidateUnreadCount(0)
                                .companyUnreadCount(4)
                                .build();

                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(chatConversationRepository.findAccessibleConversation(90L, 10L)).thenReturn(Optional.of(conversation));

                chatService.markConversationAsRead(10L, 90L);

                assertEquals(0, conversation.getCompanyUnreadCount());
                verify(chatConversationRepository).save(conversation);
        }

        @Test
        void shouldNotSaveWhenCandidateUnreadAlreadyZero() {
                ChatConversation conversation = ChatConversation.builder()
                                .id(91L)
                                .companyId(10L)
                                .candidateId(20L)
                                .candidateUnreadCount(0)
                                .companyUnreadCount(0)
                                .build();

                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(chatConversationRepository.findAccessibleConversation(91L, 20L)).thenReturn(Optional.of(conversation));

                chatService.markConversationAsRead(20L, 91L);

                verify(chatConversationRepository, never()).save(any(ChatConversation.class));
        }

    @Test
    void shouldNotSaveWhenUnreadAlreadyZeroForCompany() {
        ChatConversation conversation = ChatConversation.builder()
                .id(89L)
                .companyId(10L)
                .candidateId(20L)
                .candidateUnreadCount(0)
                .companyUnreadCount(0)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(chatConversationRepository.findAccessibleConversation(89L, 10L)).thenReturn(Optional.of(conversation));

        chatService.markConversationAsRead(10L, 89L);

        verify(chatConversationRepository, never()).save(any(ChatConversation.class));
    }

    @Test
    void shouldGetConversationSummaryWithFallbackValuesWhenVacancyMissing() {
        User namelessCounterpart = User.builder()
                .id(21L)
                .name(" ")
                .email("empty@jobswipe.com")
                .role(Role.CANDIDATE)
                .build();

        ChatConversation conversation = ChatConversation.builder()
                .id(99L)
                .vacancyId(404L)
                .companyId(10L)
                .candidateId(21L)
                .initiatedByUserId(10L)
                .candidateUnreadCount(0)
                .companyUnreadCount(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(company));
        when(userRepository.findById(21L)).thenReturn(Optional.of(namelessCounterpart));
        when(chatConversationRepository.findAccessibleConversation(99L, 10L)).thenReturn(Optional.of(conversation));
        when(vacancyRepository.findById(404L)).thenReturn(Optional.empty());

        ConversationSummaryResponse result = chatService.getConversationSummaryForUser(10L, 99L);

        assertEquals("Vacante", result.getVacancyTitle());
        assertEquals("Usuario", result.getCounterpartName());
        assertEquals(5, result.getUnreadCount());
    }

    @Test
    void shouldGetConversationSummaryForCandidateWithRealVacancyAndShortTitle() {
        User companyCounterpart = User.builder()
                .id(30L)
                .name("Empresa")
                .role(Role.COMPANY)
                .build();

        Vacancy namedVacancy = Vacancy.builder()
                .id(140L)
                .title(" ")
                .company(company)
                .build();

        ChatConversation conversation = ChatConversation.builder()
                .id(1400L)
                .vacancyId(140L)
                .companyId(10L)
                .candidateId(20L)
                .initiatedByUserId(10L)
                .candidateUnreadCount(3)
                .companyUnreadCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(userRepository.findById(10L)).thenReturn(Optional.of(companyCounterpart));
        when(chatConversationRepository.findAccessibleConversation(1400L, 20L)).thenReturn(Optional.of(conversation));
        when(vacancyRepository.findById(140L)).thenReturn(Optional.of(namedVacancy));

        ConversationSummaryResponse result = chatService.getConversationSummaryForUser(20L, 1400L);

        assertEquals("Vacante", result.getVacancyTitle());
        assertEquals("Empresa", result.getCounterpartName());
        assertEquals(3, result.getUnreadCount());
    }

        @Test
        void shouldGetConversationSummaryForCompanyWithRealVacancyAndTitle() {
                ChatConversation conversation = ChatConversation.builder()
                                .id(1410L)
                                .vacancyId(99L)
                                .companyId(10L)
                                .candidateId(20L)
                                .initiatedByUserId(10L)
                                .candidateUnreadCount(1)
                                .companyUnreadCount(4)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(chatConversationRepository.findAccessibleConversation(1410L, 10L)).thenReturn(Optional.of(conversation));
                when(vacancyRepository.findById(99L)).thenReturn(Optional.of(vacancy));

                ConversationSummaryResponse result = chatService.getConversationSummaryForUser(10L, 1410L);

                assertEquals("Java Engineer", result.getVacancyTitle());
                assertEquals("Ana", result.getCounterpartName());
                assertEquals(4, result.getUnreadCount());
        }

    @Test
    void shouldBuildShortPreviewWhenMessageIsUnderLimit() {
        ChatConversation conversation = ChatConversation.builder()
                .id(72L)
                .companyId(10L)
                .candidateId(20L)
                .candidateUnreadCount(0)
                .companyUnreadCount(0)
                .build();

        ChatMessage savedMessage = ChatMessage.builder()
                .id(702L)
                .conversationId(72L)
                .senderId(20L)
                .senderRole(Role.CANDIDATE)
                .content("short preview")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
        when(chatConversationRepository.findAccessibleConversation(72L, 20L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        chatService.sendMessage(20L, 72L, "short preview", null);

        assertEquals("short preview", conversation.getLastMessagePreview());
    }

        @Test
        void shouldReturnNullPreviewWhenContentIsBlank() {
                assertNull(org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                                chatService,
                                "buildPreview",
                                (Object) null));
                assertNull(org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                                chatService,
                                "buildPreview",
                                "   "));
        }

        @Test
        void shouldThrowWhenUserDoesNotExist() {
                when(userRepository.findById(999L)).thenReturn(Optional.empty());

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> chatService.getConversationsForUser(999L, 10));

                assertEquals("User not found.", ex.getMessage());
        }

        @Test
        void shouldThrowWhenConversationIsNotAccessible() {
                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(chatConversationRepository.findAccessibleConversation(500L, 10L)).thenReturn(Optional.empty());

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> chatService.getConversationMessages(10L, 500L, 20));

                assertEquals("Conversation not found.", ex.getMessage());
        }

        @Test
        void shouldFailStartConversationWhenVacancyNotFound() {
                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(vacancyRepository.findById(404L)).thenReturn(Optional.empty());

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> chatService.startConversation(10L, 404L, 20L));

                assertEquals("Vacancy not found.", ex.getMessage());
        }

        @Test
        void shouldFailStartConversationWhenVacancyIsNotOwnedByCompany() {
                User otherCompany = User.builder().id(30L).name("Other").role(Role.COMPANY).build();
                Vacancy foreignVacancy = Vacancy.builder().id(120L).title("Foreign").company(otherCompany).build();

                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(vacancyRepository.findById(120L)).thenReturn(Optional.of(foreignVacancy));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> chatService.startConversation(10L, 120L, 20L));

                assertEquals("Conversation can only be started from company-owned vacancies.", ex.getMessage());
        }

        @Test
        void shouldFailStartConversationWhenThereIsNoMatch() {
                when(userRepository.findById(10L)).thenReturn(Optional.of(company));
                when(userRepository.findById(20L)).thenReturn(Optional.of(candidate));
                when(vacancyRepository.findById(99L)).thenReturn(Optional.of(vacancy));
                when(companyCandidateDecisionRepository.existsByCompanyIdAndCandidateIdAndVacancyIdAndDecision(
                                10L, 20L, 99L, SwipeDecisionType.LIKE)).thenReturn(false);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> chatService.startConversation(10L, 99L, 20L));

                assertEquals("Conversation requires an active match.", ex.getMessage());
        }
}
