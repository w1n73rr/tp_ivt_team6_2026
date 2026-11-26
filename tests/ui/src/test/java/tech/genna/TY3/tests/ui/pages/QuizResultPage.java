package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

import static com.codeborne.selenide.Selenide.*;

public final class QuizResultPage extends NamedPage {
    @Name("Результат теста") public final SelenideElement title = $x("//section[@class='result']//h1");
    @Name("Название теста") public final SelenideElement quizName = $x("//section[@class='result']//p[@class='subtitle']");
    @Name("Процент результата") public final SelenideElement percent = $x("//section[@class='result']//div[@class='ring']/strong");
    @Name("Баллы") public final SelenideElement score = $x("//section[contains(@class,'score')]/p");
    @Name("Правильных") public final SelenideElement correctCount = $x("//dt[.='Правильных']/following-sibling::dd");
    @Name("Неправильных") public final SelenideElement incorrectCount = $x("//dt[.='Неправильных']/following-sibling::dd");
    @Name("Пропущено") public final SelenideElement skippedCount = $x("//dt[.='Пропущено']/following-sibling::dd");
    @Name("Всего вопросов") public final SelenideElement totalCount = $x("//dt[.='Всего вопросов']/following-sibling::dd");
    @Name("Длительность") public final SelenideElement duration = $x("//dt[.='Время прохождения']/following-sibling::dd");
    @Name("К тестам") public final SelenideElement backLink = $x("//section[@class='result']//a[.='К тестам']");
    @Name("Пройти ещё раз") public final SelenideElement repeatButton = $x("//section[@class='result']//button[normalize-space()='Пройти ещё раз']");
    @Name("Результат недоступен") public final SelenideElement unavailable = $x("//section[@class='result']//p[.='Результат не найден или недоступен.']");
    @Name("Ошибка результата") public final SelenideElement error = $x("//section[@class='result']//p[@role='alert']");
    @Name("Повторить запрос результата") public final SelenideElement retryButton = $x("//section[@class='result']//button[.='Повторить']");
    @Name("Активная попытка") public final SelenideElement unfinished = $x("//section[@class='result']//p[.='Попытка ещё не завершена.']");
    public final ElementsCollection questions = $$x("//section[@class='result']//article[contains(@class,'question')]");

    public QuizResultPage() { nameElements(); }

    public QuizResultPage openPage(String attemptId) {
        open("/attempts/" + attemptId + "/result");
        return this;
    }

    public ResultQuestion question(int index) { return new ResultQuestion(questions.get(index)); }

    public static final class ResultQuestion extends NamedPage {
        @Name("Статус ответа") public final SelenideElement outcome;
        @Name("Текст вопроса") public final SelenideElement text;
        public final ElementsCollection selectedAnswers;
        public final ElementsCollection correctAnswers;

        private ResultQuestion(SelenideElement root) {
            outcome = root.$(".badge");
            text = root.$("h3");
            selectedAnswers = root.$$(".answer.selected");
            correctAnswers = root.$$(".answer.expected");
            nameElements();
        }
    }
}
