package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

public abstract class NamedPage {
    protected final void nameElements() {
        for (var field : getClass().getFields()) {
            Name name = field.getAnnotation(Name.class);
            if (name == null) continue;
            try {
                if (field.get(this) instanceof SelenideElement element) element.as(name.value());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot name element: " + field.getName(), e);
            }
        }
    }
}
