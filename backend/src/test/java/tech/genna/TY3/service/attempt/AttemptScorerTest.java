package tech.genna.TY3.service.attempt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptScorerTest {
    final JsonMapper json=JsonMapper.builder().build();
    @ParameterizedTest @ValueSource(strings={"single_choice","multiple_choice","true_false"})
    void fullMatchScoresOneRegardlessOfOrder(String type) {
        var key=type.equals("multiple_choice")?List.of("a","b"):List.of("a");
        var questions=json.valueToTree(List.of(Map.of("id","q","type",type,"correctOptionIds",key)));
        var reversed=new ArrayList<>(key);Collections.reverse(reversed);
        var answers=json.valueToTree(Map.of("q",Map.of("selectedOptionIds",reversed)));
        assertEquals(new AttemptScorer.Score(1,1,0,0,100),AttemptScorer.score(questions,answers));
    }
    @Test void missingExtraAndDifferentOptionsScoreZero() {
        var questions=json.readTree("[{\"id\":\"q\",\"correctOptionIds\":[\"a\",\"b\"]}]");
        for(var selected:List.of(List.of("a"),List.of("a","b","c"),List.of("c"))) {
            var answers=json.valueToTree(Map.of("q",Map.of("selectedOptionIds",selected)));
            assertEquals(new AttemptScorer.Score(1,0,1,0,0),AttemptScorer.score(questions,answers));
        }
    }
    @Test void skipsAreDisjointAndPercentIsRounded() {
        var questions=json.readTree("[{\"id\":\"1\",\"correctOptionIds\":[\"a\"]},{\"id\":\"2\",\"correctOptionIds\":[\"b\"]},{\"id\":\"3\",\"correctOptionIds\":[\"c\"]}]");
        var answers=json.readTree("{\"1\":{\"selectedOptionIds\":[\"a\"]},\"2\":{\"selectedOptionIds\":[\"a\"]}}");
        assertEquals(new AttemptScorer.Score(3,1,1,1,33.33),AttemptScorer.score(questions,answers));
        assertEquals(new AttemptScorer.Score(3,0,0,3,0),AttemptScorer.score(questions,json.createObjectNode()));
        assertEquals(new AttemptScorer.Score(3,0,0,3,0),AttemptScorer.score(questions,json.readTree("{\"1\":{\"selectedOptionIds\":[]}}")));
    }
}
