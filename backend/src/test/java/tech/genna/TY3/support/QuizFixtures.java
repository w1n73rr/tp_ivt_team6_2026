package tech.genna.TY3.support;

import tech.genna.TY3.ml.GenerationRequest;
import tech.genna.TY3.ml.Quiz;

import java.util.ArrayList;
import java.util.List;

public final class QuizFixtures {
    private QuizFixtures() {}

    public static Quiz valid(GenerationRequest request) {
        var questions = new ArrayList<Quiz.Question>();
        for (int i = 0; i < request.questionCount(); i++) {
            boolean tf = request.questionType() == GenerationRequest.QuestionType.true_false;
            var options = tf ? List.of(new Quiz.Option("true", "Верно"), new Quiz.Option("false", "Неверно"))
                    : List.of(new Quiz.Option("a", "Первый вариант"), new Quiz.Option("b", "Второй вариант"),
                    new Quiz.Option("c", "Третий вариант"), new Quiz.Option("d", "Четвёртый вариант"));
            var correct = tf ? List.of(i % 2 == 0 ? "true" : "false")
                    : request.questionType() == GenerationRequest.QuestionType.multiple_choice ? List.of("a", "c") : List.of("b");
            questions.add(new Quiz.Question("q" + (i + 1), request.questionType(),
                    "Вопрос по положению " + (i + 1) + " материала", options, correct, "Объяснение по материалу " + (i + 1)));
        }
        return new Quiz(2, request.materialId(), "Название, предложенное моделью", questions);
    }
}
