# HTTP-проверка API генерации — tpivt6_26-67

Класс `GenerationApiTest` находится в Java-модуле `tests/api` и использует JUnit и REST Assured. Нужны JDK 17+ для API-тестов, Docker Desktop с Linux-контейнерами и Compose >= 2.24.4. Тесты создают пользователей, материалы и задачи в отдельной БД. Выполняйте команды из корня репозитория.

## Контролируемый прогон

Подготовьте `.env.generation.local` и запустите PostgreSQL и SeaweedFS отдельного проекта по [инструкции CRUD API](../quiz-crud/README.md).

```powershell
docker compose -p generation-acceptance --env-file .env.generation.local up -d --wait exam-ai-db seaweedfs
```

В отдельном PowerShell с JDK 25 запустите тестовый Backend. Он использует отдельную БД `generation_ui_controlled`, настоящее хранилище, парсеры документов и HTTP API. Для ошибок используются управляемый ML и внедрение сбоя в извлечение или сохранение. Не запускайте параллельно другие проверки, которые меняют режим этого стенда.

```powershell
.\deploy\scripts\start-generation-ui-test-server.ps1
```

При проблеме с Gradle Plugin Portal см. вариант с `-GradleInitScript` в инструкции CRUD API. В новом PowerShell с JDK 17+ выполните:

```powershell
$env:API_BASE_URL = 'http://localhost:18083'
$env:API_TEST_CONTROL_URL = 'http://localhost:18084'
$env:API_TEST_DISPOSABLE = 'true'
$env:API_TEST_VERSION = (git rev-parse HEAD)
.\tests\ui\gradlew.bat -p .\tests\api test -PgenerationApi
```

`gradlew.bat` лежит рядом с общим Gradle Wrapper. Параметр `-p .\tests\api` выбирает API-модуль. В IDE подключите Gradle-модуль `tests/api`, задайте те же переменные и запустите класс с исключением тега `generation-live` либо запустите отдельные методы. Для CI используйте тот же Gradle-вызов с `-PgenerationApi`.

## Прогон с настоящей ML-моделью

В локальном `.env.generation.local` настройте `OPENROUTER_API_KEY` и поддерживаемую `OPENROUTER_MODEL`. Используйте обычный Backend из Docker Compose. В этой конфигурации API может выполнить реальный запрос к выбранному провайдеру.

```powershell
docker compose -p generation-acceptance --env-file .env.generation.local up -d --build --wait exam-ai-backend
$env:API_BASE_URL = 'http://localhost:18081'
$env:API_TEST_DISPOSABLE = 'true'
$env:API_TEST_VERSION = (git rev-parse HEAD)
$env:API_TEST_LIVE_ML = 'true'
.\tests\ui\gradlew.bat -p .\tests\api test -PgenerationApiLive
```

Сценарий запускает одну генерацию по TXT и проверяет `COMPLETED` и сохранённый тест через GET списка и деталей. Отказ модели, отсутствие ключа и тайм-аут приводят к ошибке теста. Значения ключей задаются локально и не копируются в команду, Git или отчёт.

## Покрытие

| Сценарий | Ожидание |
| --- | --- |
| API-01 | GET справочника 200 для пользователя и 401 без авторизации. В справочнике 5/10 вопросов, три сложности и три типа, режим SINGLE, доступные значения по умолчанию |
| API-02 | POST 401 без авторизации, 404 для чужого или отсутствующего материала, 400 при неверных полях или ключе. Новые задачи и вызовы ML отсутствуют |
| API-03 | Три формата TXT/PDF/DOCX, оба количества, три сложности и одиночные типы. POST 202 с Location, промежуточный и конечный статус. Повтор ключа возвращает исходную задачу, изменение запроса с тем же ключом даёт 409. В списке один сохранённый тест, его детали совпадают с запросом |
| API-04 | Сбои извлечения, недоступная модель, невалидный и пустой результаты, ошибка сохранения. Статус FAILED с ожидаемым кодом, сохранённого теста нет |
| API-05 | Успешная генерация через настроенный OpenRouter и чтение результата по HTTP. Запускается отдельно |

В `tests/api/build/reports/generation-api-http.json` фиксируются ожидаемые и фактические HTTP-статусы, состояния задач и безопасные коды ошибок. Для внешней модели создаётся отдельный `generation-api-live-http.json`. HTML JUnit находится в `tests/api/build/reports/tests/test/index.html`, XML — в `tests/api/build/test-results/test/`. Отчёты исключены из Git. Настройки `API_TEST_CONTROL_URL` и `API_TEST_LIVE_ML` описаны в `.env.example`.

После проверки остановите тестовый Backend через Ctrl+C и выполните `docker compose -p generation-acceptance --env-file .env.generation.local down`.
