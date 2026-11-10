package tech.genna.TY3.ml;

/** Safe machine code; provider bodies, prompts and credentials must not be exposed. */
public final class GenerationFailure extends RuntimeException {
    public enum Code { INVALID_INPUT, NOT_CONFIGURED, RATE_LIMITED, MODEL_UNAVAILABLE,
        MODEL_TIMEOUT, MODEL_OUTPUT_LIMIT, MODEL_REFUSED, INVALID_MODEL_OUTPUT,
        QUALITY_REJECTED, INSUFFICIENT_MATERIAL, CANCELLED }
    private final Code code;
    private final String validationHint;
    public GenerationFailure(Code code) { this(code, "The response must be one valid JSON object with the required structure and no extra fields."); }
    GenerationFailure(Code code, String validationHint) {
        super(code.name()); this.code = code; this.validationHint = validationHint;
    }
    String validationHint() { return validationHint; }
    public Code code() { return code; }
}
