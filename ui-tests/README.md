# UI-тесты загрузки материалов — tpivt6_26-46

Selenide + JUnit 5, сценарии TC-UPL-01–04. Нужны запущенные Docker frontend, backend, PostgreSQL и SeaweedFS, установленный Chrome и JDK.

## Запуск кнопкой в IDEA

1. Открой проект `C:\JavaPrj`.
2. Правой кнопкой на `ui-tests/build.gradle.kts` → **Link Gradle Project** (если модуль ещё не подключён), затем обнови Gradle.
3. Открой `MaterialUploadUiTest.java` и нажми зелёный треугольник возле нужного `@Test` → **Run**. Возле класса запускаются все четыре сценария.
4. Chrome открывается с видимым окном по умолчанию. Подготовка пользователя и предмета тоже выполняется через UI. Между сценариями браузер перезапускается, после проверки выполняется очистка материалов.

Каждый метод можно запускать отдельно: `@BeforeAll` подготавливает пользователя и предмет независимо от выбранного теста. Для пошагового наблюдения поставь breakpoint на нужной строке и выбери **Debug**, затем **Step Over**.

Настройки общие для JUnit в IDEA и Gradle. Если ChromeDriver автоматически не запускается, скопируй `ui.local.properties.example` в `ui.local.properties` и укажи свой путь к совместимому драйверу. Локальный файл игнорируется Git; у Tim он уже подготовлен. Параметры JVM `-Dui.*` имеют приоритет. При обновлении Chrome нужно проверить совместимость локального драйвера.

## Структура

- `pages/LoginPage`, `RegisterPage`, `DashboardPage`, `MaterialsPage`, `UploadPage` — страницы, используемые сценарием.
- `pages/DeleteMaterialModal` — окно подтверждения удаления тестовых материалов.
- Публичные поля содержат XPath и `@Name`, например:

```java
@Name("Название материала")
public final SelenideElement nameInput = $x("//*[@id='material-name']");
```

Тест работает с ними напрямую:

```java
uploadPage.nameInput.setValue("Загрузка материала 001");
uploadPage.submitButton.click();
uploadPage.successTitle.shouldBe(visible);
materialsPage.deleteModal.confirmButton.click();
```

`support/Name` — аннотация проекта. `NamedPage` передаёт её значение в Selenide `as()`, чтобы ошибки называли элементы по-русски. `BrowserSettings` настраивает запуск браузера. Повторяемые операции подготовки и очистки оставлены методами Page Object.

## Команды

Из корня репозитория:

```powershell
docker compose up -d
.\backend\gradlew.bat -p .\ui-tests test
```

Только успешная загрузка:

```powershell
.\backend\gradlew.bat -p .\ui-tests test --tests "*MaterialUploadUiTest.successfulUpload"
```

Каждый запуск действительно выполняет тесты, без `UP-TO-DATE`. Для запуска без окна используй `-Dui.headless=true` в VM options IDEA или `$env:GRADLE_OPTS='-Dui.headless=true'` в PowerShell. Удали ранее установленные `ui.headless` из конфигурации запуска, если хочешь использовать видимый режим по умолчанию.

Отчёт: `ui-tests/build/reports/tests/test/index.html`.

При `SessionNotCreatedException` подробный журнал запуска сохраняется в `build/reports/chromedriver.log` относительно рабочей папки теста; абсолютный путь печатается в консоли. `ui.chromeBinary` в локальном файле позволяет явно выбрать установленный Chrome вместе с `ui.chromeDriver`. Предупреждения SLF4J об отсутствии провайдера не являются причиной падения Chrome.

Файлы lecture.txt и image.png создаются автоматически. Selenide передаёт файл в `input[type=file]` через `uploadFile`, поэтому системный диалог Windows не открывается. Для TC-UPL-04 ответ только на POST `/api/materials` подменяется на 500; остальные запросы идут к реальным FE/BE. Новый пользователь изолирует каждый запуск; после него материалы удаляются, учётная запись остаётся в тестовой базе.
