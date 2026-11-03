package tech.genna.TY3.ml;

/** Provider adapter. Signals the first nonempty answer content, not reasoning or keepalives. */
@FunctionalInterface
public interface ModelClient {
    String complete(String systemPrompt, String userJson, Runnable onFirstContent);
}
