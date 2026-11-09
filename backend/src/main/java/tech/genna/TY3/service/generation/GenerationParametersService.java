package tech.genna.TY3.service.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.repository.GenerationOptionsRepository;
import tech.genna.TY3.repository.GenerationOptionsRepository.Option;
import tech.genna.TY3.repository.GenerationSettingsSnapshotRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenerationParametersService {
    private final GenerationOptionsRepository options;
    private final GenerationSettingsSnapshotRepository snapshots;

    public GenerationParametersResponse getParameters() {
        var rows = catalog();
        var defaults = new GenerationSettings(QuestionCount.fromValue(Integer.valueOf(defaultValue(rows, "QUESTION_COUNT"))),
                Difficulty.fromValue(defaultValue(rows, "DIFFICULTY")),
                QuestionType.fromValue(defaultValue(rows, "QUESTION_TYPE")));
        return new GenerationParametersResponse(
                group(rows, "QUESTION_COUNT").stream()
                        .map(o -> new QuestionCountOption(QuestionCount.fromValue(Integer.valueOf(o.value())), o.available())).toList(),
                group(rows, "DIFFICULTY").stream()
                        .map(o -> new DifficultyOption(Difficulty.fromValue(o.value()), o.available())).toList(),
                group(rows, "QUESTION_TYPE").stream()
                        .map(o -> new QuestionTypeOption(QuestionType.fromValue(o.value()), o.available())).toList(),
                defaults, GenerationParametersResponse.QuestionTypeSelectionEnum.SINGLE);
    }

    public SelectedGenerationSettings validate(GenerateQuizRequest request) {
        if (request == null || request.getQuestionCount() == null || request.getDifficulty() == null
                || request.getQuestionType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        var rows = catalog();
        requireAvailable(rows, "QUESTION_COUNT", request.getQuestionCount().toString());
        requireAvailable(rows, "DIFFICULTY", request.getDifficulty().getValue());
        requireAvailable(rows, "QUESTION_TYPE", request.getQuestionType().getValue());
        return new SelectedGenerationSettings(request.getQuestionCount().getValue(), request.getDifficulty(), request.getQuestionType());
    }

    /** Called by GenerationStore.enqueue for a new accepted job, before the model is invoked. */
    @Transactional
    public SelectedGenerationSettings saveForRun(UUID jobId, GenerateQuizRequest request) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        var selected = validate(request);
        try {
            snapshots.insert(jobId, selected);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GENERATION_SETTINGS_STORAGE_UNAVAILABLE", ex);
        }
        return selected;
    }

    private List<Option> catalog() {
        final List<Option> rows;
        try {
            rows = options.findAll();
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GENERATION_CONFIG_UNAVAILABLE", ex);
        }
        // These are protocol limits, not a fallback configuration. Availability/defaults only come from SQL.
        var supported = Map.of("QUESTION_COUNT", Set.of("5", "10"),
                "DIFFICULTY", Set.of("EASY", "MEDIUM", "HARD"),
                "QUESTION_TYPE", Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"));
        if (rows.size() != 8 || rows.stream().anyMatch(o -> !supported.containsKey(o.category()))) {
            throw unavailable();
        }
        for (var entry : supported.entrySet()) {
            var category = group(rows, entry.getKey());
            if (category.size() != entry.getValue().size()
                    || !category.stream().map(Option::value).collect(Collectors.toSet()).equals(entry.getValue())
                    || category.stream().filter(Option::defaultValue).count() != 1
                    || category.stream().anyMatch(o -> o.defaultValue() && !o.available())) {
                throw unavailable();
            }
        }
        return rows;
    }

    private static List<Option> group(List<Option> rows, String category) {
        return rows.stream().filter(o -> o.category().equals(category)).toList();
    }

    private static String defaultValue(List<Option> rows, String category) {
        return group(rows, category).stream().filter(Option::defaultValue).findFirst().orElseThrow().value();
    }

    private static void requireAvailable(List<Option> rows, String category, String value) {
        if (group(rows, category).stream().noneMatch(o -> o.value().equals(value) && o.available())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GENERATION_OPTION_NOT_AVAILABLE");
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GENERATION_CONFIG_UNAVAILABLE");
    }
}
