package tech.genna.TY3.service.attempt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tech.genna.TY3.api.model.SaveAnswerRequest;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class SaveAnswerDeserializerTest {
    final JsonMapper json=JsonMapper.builder().build();
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"selectedOptionIds\":null}",
            "{\"selectedOptionIds\":[null]}","{\"selectedOptionIds\":[\"bad-id\"]}",
            "{\"selectedOptionIds\":[],\"score\":100}","{\"selectedOptionIds\":[],\"timeSpentSeconds\":1.1}"})
    void invalidPayloadsAreRejected(String payload) {
        // A JSON null body is rejected by Spring's required request body, not this deserializer.
        if(payload.equals("null")) {assertNull(json.readValue(payload,SaveAnswerRequest.class));return;}
        assertThrows(Exception.class,()->json.readValue(payload,SaveAnswerRequest.class));
    }
    @Test void explicitEmptySelectionIsAccepted() {
        assertTrue(json.readValue("{\"selectedOptionIds\":[]}",SaveAnswerRequest.class).getSelectedOptionIds().isEmpty());
    }
}
