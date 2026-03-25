package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.response.MessageResponse;
import ro.unibuc.prodeng.service.MessageService;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
public class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageController messageController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private MessageResponse testMessageResponse;
    private CreateMessageRequest validRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        
        testMessageResponse = new MessageResponse(
                "msg-1", 
                "Hello Team!", 
                "team-1", 
                "user-1", 
                Instant.parse("2023-01-01T10:00:00Z"), 
                false
        );
        validRequest = new CreateMessageRequest("Hello Team!");
    }

    @Test
    void testCreateMessage_withValidRequest_returnsCreatedAndMessage() throws Exception {

        // Arrange
        when(messageService.createMessage(eq("team-1"), any(CreateMessageRequest.class)))
                .thenReturn(testMessageResponse);

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/messages", "team-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("msg-1")))
                .andExpect(jsonPath("$.content", is("Hello Team!")))
                .andExpect(jsonPath("$.teamId", is("team-1")))
                .andExpect(jsonPath("$.sentBy", is("user-1")))
                .andExpect(jsonPath("$.sentAt", is("2023-01-01T10:00:00Z")))
                .andExpect(jsonPath("$.isTruncated", is(false)));

        verify(messageService, times(1)).createMessage(eq("team-1"), any(CreateMessageRequest.class));
    }

    @Test
    void testCreateMessage_withBlankContent_returnsBadRequest() throws Exception {

        // Arrange
        CreateMessageRequest invalidRequest = new CreateMessageRequest("   ");

        // Act & Assert
        mockMvc.perform(post("/api/teams/{teamId}/messages", "team-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // Verify that the service is never called if validation fails
        verify(messageService, never()).createMessage(anyString(), any(CreateMessageRequest.class));
    }

    @Test
    void testGetMessagesByTeam_withoutParams_returnsList() throws Exception {

        // Arrange
        List<MessageResponse> messages = Collections.singletonList(testMessageResponse);
        when(messageService.getMessagesByTeam("team-1", null, null)).thenReturn(messages);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages", "team-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("msg-1")))
                .andExpect(jsonPath("$[0].content", is("Hello Team!")));

        verify(messageService, times(1)).getMessagesByTeam("team-1", null, null);
    }

    @Test
    void testGetMessagesByTeam_withParams_returnsList() throws Exception {

        // Arrange
        List<MessageResponse> messages = Collections.singletonList(testMessageResponse);
        when(messageService.getMessagesByTeam("team-1", "cursor-123", 10)).thenReturn(messages);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages", "team-1")
                .param("cursor", "cursor-123")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("msg-1")));

        verify(messageService, times(1)).getMessagesByTeam("team-1", "cursor-123", 10);
    }

    @Test
    void testGetMessagesByTeam_whenEmpty_returnsEmptyList() throws Exception {

        // Arrange
        when(messageService.getMessagesByTeam("team-2", null, null)).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages", "team-2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(messageService, times(1)).getMessagesByTeam("team-2", null, null);
    }

    @Test
    void testGetMessageById_withValidIds_returnsMessage() throws Exception {

        // Arrange
        when(messageService.getMessageById("team-1", "msg-1")).thenReturn(testMessageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", "team-1", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("msg-1")))
                .andExpect(jsonPath("$.content", is("Hello Team!")))
                .andExpect(jsonPath("$.teamId", is("team-1")))
                .andExpect(jsonPath("$.sentBy", is("user-1")))
                .andExpect(jsonPath("$.sentAt", is("2023-01-01T10:00:00Z")))
                .andExpect(jsonPath("$.isTruncated", is(false)));

        verify(messageService, times(1)).getMessageById("team-1", "msg-1");
    }

    @Test
    void testGetMessageById_whenTeamNotFound_returnsNotFound() throws Exception {

        // Arrange
        when(messageService.getMessageById("missing-team", "msg-1"))
                .thenThrow(new EntityNotFoundException("Team with id missing-team not found"));

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", "missing-team", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("missing-team")));

        verify(messageService, times(1)).getMessageById("missing-team", "msg-1");
    }

    @Test
    void testGetMessageById_whenMessageNotFound_returnsNotFound() throws Exception {

        // Arrange
        when(messageService.getMessageById("team-1", "missing-msg"))
                .thenThrow(new EntityNotFoundException("Message with id: missing-msg and team: team-1"));

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", "team-1", "missing-msg")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("missing-msg")));

        verify(messageService, times(1)).getMessageById("team-1", "missing-msg");
    }

    @Test
    void testGetMessageById_whenForbidden_returnsForbidden() throws Exception {

        // Arrange
        when(messageService.getMessageById("team-1", "msg-1"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        // Act & Assert
        mockMvc.perform(get("/api/teams/{teamId}/messages/{messageId}", "team-1", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(messageService, times(1)).getMessageById("team-1", "msg-1");
    }

    @Test
    void testDeleteMessage_withValidIds_returnsNoContent() throws Exception {

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/messages/{messageId}", "team-1", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(messageService, times(1)).deleteMessage("team-1", "msg-1");
    }

    @Test
    void testDeleteMessage_whenTeamNotFound_returnsNotFound() throws Exception {

        // Arrange
        doThrow(new EntityNotFoundException("Team with id: missing-team"))
                .when(messageService).deleteMessage("missing-team", "msg-1");

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/messages/{messageId}", "missing-team", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("missing-team")));

        verify(messageService, times(1)).deleteMessage("missing-team", "msg-1");
    }

    @Test
    void testDeleteMessage_whenMessageNotFound_returnsNotFound() throws Exception {

        // Arrange
        doThrow(new EntityNotFoundException("Message with id: missing-msg and team: team-1"))
                .when(messageService).deleteMessage("team-1", "missing-msg");

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/messages/{messageId}", "team-1", "missing-msg")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("missing-msg")));

        verify(messageService, times(1)).deleteMessage("team-1", "missing-msg");
    }

    @Test
    void testDeleteMessage_whenForbidden_returnsForbidden() throws Exception {

        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                .when(messageService).deleteMessage("team-1", "msg-1");

        // Act & Assert
        mockMvc.perform(delete("/api/teams/{teamId}/messages/{messageId}", "team-1", "msg-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(messageService, times(1)).deleteMessage("team-1", "msg-1");
    }
}