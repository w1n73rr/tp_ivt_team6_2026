package tech.genna.TY3.service.generation;

/** Only allowlisted codes are returned to the owner; dependency messages stay private. */
public class GenerationFailure extends RuntimeException {
    private final String code;
    public GenerationFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
