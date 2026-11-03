package tech.genna.TY3.ml;

/** Stages of ML only. VALIDATED is not a saved quiz or a successful BE job. */
public record GenerationEvent(String requestId, Stage stage, long sequence) {
    public enum Stage { PREPARING_PROMPT, WAITING_FOR_MODEL, RECEIVING_RESPONSE, VALIDATING,
        REVIEWING_CONTENT, REVISING, VALIDATED, FAILED }
}
