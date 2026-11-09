package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

/** Elements scoped to one material; snapshots contain only data visible in the UI. */
public final class MaterialRow extends NamedPage {
    @Name("Строка материала") public final SelenideElement row;
    @Name("Название материала") public final SelenideElement title;
    @Name("Предмет, формат и размер") public final SelenideElement metadata;
    @Name("Формат материала") public final SelenideElement format;
    @Name("Дата загрузки") public final SelenideElement uploadedAt;
    @Name("Изменить материал") public final SelenideElement editButton;
    @Name("Удалить материал") public final SelenideElement deleteButton;
    @Name("Создать тест по материалу") public final SelenideElement generateButton;

    public MaterialRow(SelenideElement row) {
        this.row = row;
        title = row.$x(".//h2");
        metadata = row.$x(".//div[@class='info']/small");
        format = row.$x(".//span[contains(concat(' ',normalize-space(@class),' '),' format ')]");
        uploadedAt = row.$x(".//time");
        editButton = row.$x(".//button[@aria-label='Изменить материал']");
        deleteButton = row.$x(".//button[@aria-label='Удалить материал']");
        generateButton = row.$x(".//button[contains(normalize-space(.),'Создать тест')]");
        nameElements();
    }

    public Snapshot snapshot() {
        String[] values = metadata.getText().split("\\s*·\\s*");
        if (values.length != 3) throw new AssertionError("Unexpected material metadata: " + metadata.getText());
        return new Snapshot(title.getText(), values[0], format.getText(), values[2], uploadedAt.getText());
    }

    public record Snapshot(String name, String subject, String format, String size, String date) {
        public Snapshot renamed(String newName) { return new Snapshot(newName, subject, format, size, date); }
        public Snapshot withSubject(String newSubject) { return new Snapshot(name, newSubject, format, size, date); }
    }
}
