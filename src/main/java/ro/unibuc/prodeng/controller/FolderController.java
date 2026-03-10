package ro.unibuc.prodeng.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.unibuc.prodeng.response.FolderResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.service.FolderService;

@RestController
@RequestMapping("/api/folders/{teamId}")
public class FolderController {

    @Autowired
    private FolderService folderService;

    @GetMapping("/{teamId}")
    public ResponseEntity<List<FolderResponse>> getAllFolders(@PathVariable String teamId) throws EntityNotFoundException {
        List<FolderResponse> folders = folderService.getAllFolders(teamId);
        return ResponseEntity.ok(folders);
    }

}
