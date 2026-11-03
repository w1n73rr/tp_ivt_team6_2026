package tech.genna.TY3.ml;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

final class Json {
    private static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private Json() {}
    static String write(Object value) { return MAPPER.writeValueAsString(value); }
    static Map<?, ?> read(String value) {
        try { return MAPPER.readValue(value, Map.class); }
        catch (RuntimeException ex) { throw new GenerationFailure(GenerationFailure.Code.INVALID_MODEL_OUTPUT); }
    }
}
