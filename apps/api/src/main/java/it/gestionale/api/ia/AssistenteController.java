package it.gestionale.api.ia;

import it.gestionale.api.accesso.Ruolo;

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
public class AssistenteController {

    private final AssistenteService assistantService;

    public AssistenteController(AssistenteService assistantService) {
        this.assistantService = assistantService;
    }

    public record MessaggioChat(@NotNull MessaggioIa.Ruolo role, @NotBlank String content) {
    }

    public record RichiestaChat(@NotEmpty List<@Valid MessaggioChat> messages) {
    }

    public record RispostaChat(String content, String model) {
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('assistant:use')")
    public RispostaChat chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RichiestaChat request) {
        List<MessaggioIa> messages = request.messages().stream()
                .map(m -> new MessaggioIa(m.role(), m.content()))
                .toList();
        RispostaIa response = assistantService.chat(jwt.getSubject(), messages);
        return new RispostaChat(response.content(), response.model());
    }
}
