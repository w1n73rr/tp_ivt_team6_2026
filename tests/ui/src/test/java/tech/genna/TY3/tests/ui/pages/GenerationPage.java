package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ElementsCollection;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Condition.*;

public final class GenerationPage extends NamedPage {
    @Name("Заголовок генерации") public final SelenideElement title = $x("//app-generation//h1");
    @Name("Карточка материала") public final SelenideElement materialCard = $x("//app-generation//div[@class='material']");
    @Name("Название теста") public final SelenideElement nameInput = $x("//app-generation//input[@id='test-name']");
    @Name("Пять вопросов") public final SelenideElement fiveQuestions = $x("//app-generation//label[input[@name='count']][contains(.,'5 вопросов')]/input");
    @Name("Десять вопросов") public final SelenideElement tenQuestions = $x("//app-generation//label[input[@name='count']][contains(.,'10 вопросов')]/input");
    @Name("Лёгкая сложность") public final SelenideElement easy = $x("//app-generation//label[input[@name='difficulty']][contains(.,'Лёгкая')]/input");
    @Name("Средняя сложность") public final SelenideElement medium = $x("//app-generation//label[input[@name='difficulty']][contains(.,'Средняя')]/input");
    @Name("Сложная сложность") public final SelenideElement hard = $x("//app-generation//label[input[@name='difficulty']][contains(.,'Сложная')]/input");
    @Name("Один ответ") public final SelenideElement singleChoice = $x("//app-generation//label[input[@name='type']][contains(.,'Один ответ')]/input");
    @Name("Несколько ответов") public final SelenideElement multipleChoice = $x("//app-generation//label[input[@name='type']][contains(.,'Несколько ответов')]/input");
    @Name("Верно/неверно") public final SelenideElement trueFalse = $x("//app-generation//label[input[@name='type']][contains(.,'Верно/неверно')]/input");
    @Name("Сводка") public final SelenideElement summary = $x("//app-generation//div[@aria-live='polite']");
    @Name("Создать тест") public final SelenideElement createButton = $x("//app-generation//button[normalize-space()='Создать тест']");
    @Name("Назад к материалам") public final SelenideElement backLink = $x("//app-generation//a[normalize-space()='Назад к материалам']");
    @Name("Материалы в навигации") public final SelenideElement materialsBreadcrumb = $x("//app-generation//nav//a[@href='/materials']");
    @Name("Дата материала в карточке") public final SelenideElement materialDate = materialCard.$x(".//time");
    public final ElementsCollection forbiddenControls = $$x("//app-generation//select | //app-generation//input[@type='checkbox'] | //app-generation//textarea");
    public final ElementsCollection countOptions = $$x("//app-generation//input[@name='count']");
    @Name("Демонстрационный режим") public final SelenideElement demoNotice = $x("//app-generation//p[@class='demo']");
    @Name("Ожидание") public final SelenideElement waitingPanel = $x("//app-generation//section[@role='status']");
    @Name("Готовый тест") public final SelenideElement readyPanel = $x("//app-generation//section[contains(concat(' ',normalize-space(@class),' '),' result ')]");
    @Name("Все тесты") public final SelenideElement readyLink = $x("//app-generation//a[normalize-space()='Все тесты']");
    @Name("Ошибка формы") public final SelenideElement validationError = $x("//app-generation//div[@role='alert']");
    @Name("Ошибка генерации") public final SelenideElement failurePanel = $x("//app-generation//section[@role='alert']");
    @Name("Повторить генерацию") public final SelenideElement retryButton = $x("//app-generation//button[normalize-space()='Повторить генерацию']");
    @Name("К настройкам") public final SelenideElement settingsButton = $x("//app-generation//button[normalize-space()='К настройкам']");
    @Name("Демо ошибки — только проверка интерфейса") public final SelenideElement demoFailureButton = $x("//app-generation//button[normalize-space()='Демо: ошибка']");
    public final ElementsCollection selectedTypes = $$x("//app-generation//input[@name='type']").filterBy(selected);

    public GenerationPage() { nameElements(); }
    public GenerationPage shouldBeOpen() {
        title.shouldHave(exactText("Настройка генерации теста"));
        nameInput.shouldBe(visible);
        return this;
    }
}
