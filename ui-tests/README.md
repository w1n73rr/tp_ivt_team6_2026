# UI-тесты загрузки материалов

Сценарии `TC-UPL-01`–`TC-UPL-04` реализованы на Selenide и JUnit 5 в `MaterialUploadUiTest`.
Нужны запущенные frontend, backend, PostgreSQL и файловое хранилище из корневого `docker-compose.yml`, а также установленный Chrome.

Тест использует Page Object. Локаторы находятся только в классах `pages/`, поэтому сценарии вызывают действия страниц, не обращаясь к CSS-селекторам напрямую:

| Page Object | Основные локаторы | Для чего |
| --- | --- | --- |
| `RegisterPage` | `#username`, `#email`, `#password`, `#confirmPassword`, `button[type=submit]` | Создание изолированного пользователя |
| `LoginPage` | `#username`, `#password`, `button[type=submit]` | Вход в каждой новой браузерной сессии |
| `MaterialsPage` | `section[aria-label='Список материалов']`, `.list article`, `header button` | Открытие формы и проверка списка после загрузки |
| `UploadPage` | `input[type=file]`, `.chosen-file`, `#material-name`, `#subject`, `#description`, `#tag-input`, `.success`, `[role=alert]` | Выбор файла, заполнение формы, проверки успеха и ошибок |

Файл передаётся в `input[type=file]` методом Selenide `uploadFile`; системный диалог выбора файла не участвует в автоматизации.

Из корня репозитория:

```powershell
docker compose up --build -d
.\backend\gradlew.bat -p .\ui-tests test
```

В PowerShell адрес приложения и видимый режим браузера задаются так:

```powershell
$env:GRADLE_OPTS='-Dui.baseUrl=http://localhost:3000 -Dui.headless=false'
.\backend\gradlew.bat -p .\ui-tests test
```

По умолчанию браузер работает без окна. В ограниченной среде Codex для запуска Chrome дополнительно потребовалось `-Dui.disableSandbox=true`; в обычном PowerShell этот параметр не нужен.
Отчёт Gradle: `ui-tests/build/reports/tests/test/index.html`.

Тест создаёт отдельного пользователя, добавляет ему предмет «Базы данных» через UI и после прогона удаляет созданные материалы. Уникальный пользователь и подготовленный файл позволяют повторить прогон без ручной очистки. Учётная запись остаётся в тестовой базе.

В `TC-UPL-04` только запрос `POST /api/materials` подменяется ответом 500 в браузере. Остальные запросы идут к работающим FE и BE; это позволяет проверить сообщение об ошибке и отсутствие материала в настоящем списке.
