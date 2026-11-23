package it.gestionale.api.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    public record ChatMessage(@NotNull AiMessage.Role role, @NotBlank String content) {
    }

    public record ChatRequest(@NotEmpty List<@Valid ChatMessage> messages) {
    }

    public record ChatReply(String content, String model) {
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('assistant:use')")
    public ChatReply chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
        List<AiMessage> messages = request.messages().stream()
                .map(m -> new AiMessage(m.role(), m.content()))
                .toList();
        AiResponse response = assistantService.chat(jwt.getSubject(), messages);
        return new ChatReply(response.content(), response.model());
    }
}
