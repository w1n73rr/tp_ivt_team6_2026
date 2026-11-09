package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ElementsCollection;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.$x;

public final class DeleteMaterialModal extends NamedPage {
    @Name("Окно подтверждения удаления")
    public final SelenideElement modal = $x("//*[@role='dialog'][.//app-material-delete-confirm]");
    @Name("Заголовок подтверждения удаления")
    public final SelenideElement title = modal.$x(".//h2");
    @Name("Предупреждение об удалении файла")
    public final SelenideElement warning = modal.$x(".//mat-dialog-content/p");
    public final ElementsCollection materialNames = modal.$$x(".//ul/li");
    @Name("Подтвердить удаление")
    public final SelenideElement confirmButton = modal.$x(".//button[normalize-space()='Удалить']");
    @Name("Отменить удаление")
    public final SelenideElement cancelButton = modal.$x(".//button[normalize-space()='Отмена']");

    public DeleteMaterialModal() { nameElements(); }
}
