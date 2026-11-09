package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.$x;

public final class EditMaterialModal extends NamedPage {
    @Name("Окно редактирования материала")
    public final SelenideElement modal = $x("//*[@role='dialog'][.//app-material-editor]");
    @Name("Название материала")
    public final SelenideElement nameInput = modal.$x(".//*[@id='edit-name']");
    @Name("Предмет материала")
    public final SelenideElement subjectInput = modal.$x(".//*[@id='edit-subject']");
    @Name("Сохранить изменения")
    public final SelenideElement saveButton = modal.$x(".//button[@type='submit']");
    @Name("Отменить редактирование")
    public final SelenideElement cancelButton = modal.$x(".//button[normalize-space()='Отмена']");
    @Name("Ошибка сохранения")
    public final SelenideElement errorMessage = modal.$x(".//*[@role='alert']");
    public final ElementsCollection editableFields = modal.$$x(".//input[not(@type='hidden') and not(@readonly) and not(@disabled)] | .//select[not(@disabled)] | .//textarea[not(@readonly) and not(@disabled)]");

    public EditMaterialModal() { nameElements(); }
}
