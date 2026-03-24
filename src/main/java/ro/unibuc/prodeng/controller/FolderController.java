package ro.unibuc.prodeng.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.response.FolderResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.request.CreateFolderRequest;
import ro.unibuc.prodeng.service.FolderService;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    @Autowired
    private FolderService folderService;

    @GetMapping("/{teamId}")
    public ResponseEntity<List<FolderResponse>> getAllFolders(@PathVariable String teamId) throws EntityNotFoundException {
        List<FolderResponse> folders = folderService.getAllFolders(teamId);
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/subfolders/{folderId}")
    public ResponseEntity<List<FolderResponse>> getSubFolders(@PathVariable String folderId) throws EntityNotFoundException {
        List<FolderResponse> folders = folderService.getSubFolders( folderId);
        return ResponseEntity.ok(folders);
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
           @Valid @RequestBody CreateFolderRequest request) throws EntityNotFoundException {
        FolderResponse folder = folderService.createFolder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder); 
    }

    @PatchMapping("/{folderId}/name")
    public ResponseEntity<FolderResponse> updateFolderName(@PathVariable String folderId, @RequestBody String name) throws EntityNotFoundException {
        FolderResponse folder = folderService.updateFolderName(folderId, name);
        return ResponseEntity.ok(folder);
    }
    
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String folderId) throws EntityNotFoundException {
        folderService.deleteFolder(folderId);
        return ResponseEntity.noContent().build();
    }

}
