package ro.unibuc.prodeng.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.CreateMessageRequest;
import ro.unibuc.prodeng.response.MessageResponse;
import ro.unibuc.prodeng.service.MessageService;

@RestController
@RequestMapping("/api/teams/{teamId}/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable String teamId,
            @Valid @RequestBody CreateMessageRequest request) {
        MessageResponse createdMessage = messageService.createMessage(teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMessage);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessagesByTeam(
            @PathVariable String teamId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        List<MessageResponse> messages = messageService.getMessagesByTeam(teamId, cursor, limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<MessageResponse> getMessageById(
            @PathVariable String teamId,
            @PathVariable String messageId) {
        MessageResponse message = messageService.getMessageById(teamId, messageId);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String teamId,
            @PathVariable String messageId) {
        messageService.deleteMessage(teamId, messageId);
        return ResponseEntity.noContent().build();
    }
}