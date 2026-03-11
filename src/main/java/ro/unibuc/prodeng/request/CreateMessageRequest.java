package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

//Enrollment&access: admin controls members/content -> Payload for creating messages inside a controlled team scope.
public record CreateMessageRequest(
        @NotBlank(message = "Content is required")
        String content
) {
}