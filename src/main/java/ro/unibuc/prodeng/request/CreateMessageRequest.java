package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(

        // A message cannot be blank or have only whitespaces.
        // A maximum size of 8 000 characters was enforced in order to avoid network or application latency

        @NotBlank(message = "Content is required")
        @Size(max = 8000, message = "Content cannot exceed 8 000 characters")
        String content
) {
}