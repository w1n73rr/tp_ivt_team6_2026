package tech.genna.TY3.service.attempt;

import tech.genna.TY3.api.model.SaveAnswerRequest;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import java.util.*;

/** Reject absent selections instead of treating a generated collection default as an intentional clear. */
public class SaveAnswerDeserializer extends ValueDeserializer<SaveAnswerRequest> {
    @Override public SaveAnswerRequest deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node=context.readTree(parser);
        if(!node.isObject() || node.properties().stream().anyMatch(p ->
                !Set.of("selectedOptionIds","timeSpentSeconds").contains(p.getKey()))) throw invalid();
        var values=node.get("selectedOptionIds");
        if(values==null || !values.isArray() || values.size()>4) throw invalid();
        var ids=new LinkedHashSet<UUID>();
        for(var value:values) {
            if(!value.isString()) throw invalid();
            UUID id;
            try {id=UUID.fromString(value.asText());} catch(IllegalArgumentException ex) {throw invalid();}
            if(!id.toString().equalsIgnoreCase(value.asText()) || !ids.add(id)) throw invalid();
        }
        var request=new SaveAnswerRequest(ids);
        var seconds=node.get("timeSpentSeconds");
        if(seconds!=null) {
            if(!seconds.isIntegralNumber() || !seconds.canConvertToInt() || seconds.intValue()<0 || seconds.intValue()>86400) throw invalid();
            request.setTimeSpentSeconds(seconds.intValue());
        }
        return request;
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("INVALID_ANSWER");}
}
