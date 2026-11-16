package tech.genna.TY3.service.attempt;

import tools.jackson.databind.JsonNode;
import java.math.*;
import java.util.*;

/** Pure server-side scoring over immutable questions and selections locked by the caller. */
public final class AttemptScorer {
    private AttemptScorer() {}
    public record Score(int total,int correct,int incorrect,int skipped,double percent) {}
    public static Score score(JsonNode questions,JsonNode answers) {
        int correct=0,skipped=0;
        if(!questions.isArray() || questions.isEmpty()) throw new IllegalStateException("INVALID_ATTEMPT_SNAPSHOT");
        for(var q:questions) {
            var answer=answers.path(q.path("id").asText()).path("selectedOptionIds");
            if(answer.isMissingNode() || answer.isEmpty()) skipped++;
            else if(ids(answer).equals(ids(q.path("correctOptionIds")))) correct++;
        }
        int total=questions.size();
        double percent=BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP).doubleValue();
        return new Score(total,correct,total-correct-skipped,skipped,percent);
    }
    static Set<String> ids(JsonNode values) {
        var ids=new HashSet<String>(); for(var value:values) ids.add(value.asText());return ids;
    }
}
