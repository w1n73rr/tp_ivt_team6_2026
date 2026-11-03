package tech.genna.TY3.tests.ui.support;

import java.lang.annotation.*;

/** Human-readable name used by NamedPage in Selenide diagnostics. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Name {
    String value();
}
