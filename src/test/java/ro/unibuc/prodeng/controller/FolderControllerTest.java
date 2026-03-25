package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.request.CreateFolderRequest;
import ro.unibuc.prodeng.response.FolderResponse;
import ro.unibuc.prodeng.service.FolderService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
class FolderControllerTest {

    @Mock
    private FolderService folderService;

    @InjectMocks
    private FolderController folderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private FolderResponse mockResponse1;
    private FolderResponse mockResponse2;

    @BeforeEach
    void setUp() {
        // Initialize MockMvc and attach GlobalExceptionHandler
        mockMvc = MockMvcBuilders.standaloneSetup(folderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockResponse1 = new FolderResponse("folder-1", "Project A", "Team Backend", null, Instant.now(), Instant.now());
        mockResponse2 = new FolderResponse("folder-2", "Project B", "Team Backend", null, Instant.now(), Instant.now());
    }

    @Test
    void testGetAllFolders_existingTeam_returnsOkAndList() throws Exception {
        // Arrange
        String teamId = "team-1";
        List<FolderResponse> folders = Arrays.asList(mockResponse1, mockResponse2);
        when(folderService.getAllFolders(teamId)).thenReturn(folders);

        // Act & Assert
        mockMvc.perform(get("/api/folders/{teamId}", teamId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("folder-1")))
                .andExpect(jsonPath("$[0].name", is("Project A")))
                .andExpect(jsonPath("$[1].id", is("folder-2")));

        verify(folderService, times(1)).getAllFolders(teamId);
    }

    @Test
    void testGetAllFolders_nonExistingTeam_returnsNotFound() throws Exception {
        // Arrange
        String teamId = "invalid-team";
        when(folderService.getAllFolders(teamId)).thenThrow(new EntityNotFoundException("Team with id: " + teamId));

        // Act & Assert
        mockMvc.perform(get("/api/folders/{teamId}", teamId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists()); // Check that GlobalExceptionHandler send message
        
        verify(folderService, times(1)).getAllFolders(teamId);
    }

    @Test
    void testCreateFolder_validRequest_returnsCreated() throws Exception {
        // Arrange
        CreateFolderRequest request = new CreateFolderRequest("New Folder", "team-1", null);
        FolderResponse response = new FolderResponse("folder-3", "New Folder", "Team Backend", null, Instant.now(), Instant.now());
        
        when(folderService.createFolder(any(CreateFolderRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("folder-3")))
                .andExpect(jsonPath("$.name", is("New Folder")));

        verify(folderService, times(1)).createFolder(any(CreateFolderRequest.class));
    }

    @Test
    void testCreateFolder_invalidRequestMissingFields_returnsBadRequest() throws Exception {
        // Arrange
        // Name and TeamId are blank
        CreateFolderRequest invalidRequest = new CreateFolderRequest("", "", null);

        // Act & Assert
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        
        verify(folderService, never()).createFolder(any(CreateFolderRequest.class));
    }

    @Test
    void testUpdateFolderName_validRequest_returnsOk() throws Exception {
        // Arrange
        String folderId = "folder-1";
        String newName = "Updated Name";
        FolderResponse updatedResponse = new FolderResponse(folderId, newName, "Team Backend", null, Instant.now(), Instant.now());
        
        when(folderService.updateFolderName(folderId, newName)).thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(patch("/api/folders/{folderId}/name", folderId)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(newName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(newName)));

        verify(folderService, times(1)).updateFolderName(folderId, newName);
    }

    @Test
    void testUpdateFolderName_insufficientPermissions_returnsForbidden() throws Exception {
        // Arrange
        String folderId = "folder-1";
        String newName = "Secret Project";
        
        when(folderService.updateFolderName(folderId, newName))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You are not allowed"));

        // Act & Assert
        mockMvc.perform(patch("/api/folders/{folderId}/name", folderId)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(newName))
                .andExpect(status().isForbidden());
        
        verify(folderService, times(1)).updateFolderName(folderId, newName);
    }

    @Test
    void testDeleteFolder_existingFolder_returnsNoContent() throws Exception {
        // Arrange
        String folderId = "folder-1";
        doNothing().when(folderService).deleteFolder(folderId);

        // Act & Assert
        mockMvc.perform(delete("/api/folders/{folderId}", folderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(folderService, times(1)).deleteFolder(folderId);
    }
    
    @Test
    void testDeleteFolder_nonExistingFolder_returnsNotFound() throws Exception {
        // Arrange
        String folderId = "invalid-folder";
        doThrow(new EntityNotFoundException("invalid-folder")).when(folderService).deleteFolder(folderId);

        // Act & Assert
        mockMvc.perform(delete("/api/folders/{folderId}", folderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(folderService, times(1)).deleteFolder(folderId);
    }
}