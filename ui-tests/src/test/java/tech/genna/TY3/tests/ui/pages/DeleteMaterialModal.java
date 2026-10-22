package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.$x;

public final class DeleteMaterialModal extends NamedPage {
    @Name("Окно подтверждения удаления")
    public final SelenideElement modal = $x("//*[@role='dialog']");
    @Name("Подтвердить удаление")
    public final SelenideElement confirmButton = modal.$x(".//button[normalize-space()='Удалить']");

    public DeleteMaterialModal() { nameElements(); }
}
