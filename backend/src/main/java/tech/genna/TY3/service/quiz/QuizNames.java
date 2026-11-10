package tech.genna.TY3.service.quiz;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class QuizNames {
    private QuizNames() {}

    static String normalize(String name) {
        if (name == null) throw invalid();
        // Match FE String.trim(), including Unicode space characters and BOM.
        String trimmed = name.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
        if (trimmed.isBlank() || trimmed.length() > 100) throw invalid();
        return trimmed;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QUIZ_NAME");
    }
}
