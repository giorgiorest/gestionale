package it.gestionale.api.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** Richiesta del modello di invocare un tool, con gli argomenti in JSON. */
public record AiToolCall(String toolName, JsonNode arguments) {
}
