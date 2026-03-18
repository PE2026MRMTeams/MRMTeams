package ro.unibuc.prodeng.request;
import jakarta.validation.constraints.NotBlank;

public record CreateFolderRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Team ID is required")
    String teamId,

    String parentFolderId
) {}