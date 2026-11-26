package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.*;

public final class QuizAttemptPage extends NamedPage {
    @Name("Прохождение теста") public final SelenideElement title = $x("//h1[.='Прохождение теста']");
    @Name("Позиция вопроса") public final SelenideElement position = $x("//header[@class='attempt-progress']/p");
    @Name("Текст текущего вопроса") public final SelenideElement question = $x("//app-attempt//legend");
    @Name("Далее") public final SelenideElement nextButton = $x("//app-attempt//button[.='Далее']");
    @Name("Завершить тест") public final SelenideElement finishButton = $x("//app-attempt//button[.='Завершить тест']");
    @Name("Подтвердить завершение") public final SelenideElement confirmButton = $x("//app-attempt//button[.='Завершить']");
    public final ElementsCollection options = $$x("//app-attempt//fieldset//label");
    public final ElementsCollection selectedOptions = $$("app-attempt fieldset input:checked");
    public QuizAttemptPage() { nameElements(); }
}
