package it.gestionale.api.ia;

import com.fasterxml.jackson.databind.JsonNode;

/** Richiesta del modello di invocare un tool, con gli argomenti in JSON. */
public record ChiamataStrumentoIa(String toolName, JsonNode arguments) {
}
