# Справочник настроек генерации TY5

## Контракт BE / FE / ML

`GET /api/generation/parameters`, `Authorization: Bearer <JWT>`:

```json
{
  "questionCounts": [{"value":5,"available":true},{"value":10,"available":true}],
  "difficulties": [{"value":"EASY","available":true},{"value":"MEDIUM","available":true},{"value":"HARD","available":true}],
  "questionTypes": [{"value":"SINGLE_CHOICE","available":true},{"value":"MULTIPLE_CHOICE","available":true},{"value":"TRUE_FALSE","available":true}],
  "defaults": {"questionCount":10,"difficulty":"MEDIUM","questionType":"SINGLE_CHOICE"},
  "questionTypeSelection":"SINGLE"
}
```

Это начальное состояние БД. Defaults 10 / MEDIUM / SINGLE_CHOICE явно
подтверждены пользователем 03.11.2026.

FE показывает все значения и блокирует недоступные; тип выбирается ровно один.
Названия: EASY — лёгкая, MEDIUM — средняя, HARD — сложная;
SINGLE_CHOICE — один ответ, MULTIPLE_CHOICE — несколько ответов,
TRUE_FALSE — верно/неверно. Порядок задаётся sort_order в БД, value служит вторым ключом.
Тексты подписей FE локализует самостоятельно. `Cache-Control: no-store` исключает
использование старого справочника HTTP-кешем. Сервер перечитывает БД на каждый запрос.

У `POST /api/quizzes` обязательны Bearer JWT, UUID-заголовок `Idempotency-Key` и тело:

```json
{"materialId":"00000000-0000-0000-0000-000000000001","questionCount":5,"difficulty":"MEDIUM","questionType":"SINGLE_CHOICE"}
```

Все четыре поля обязательны. Defaults предназначены для начального выбора FE,
а не для молчаливого дополнения неполного запроса сервером. Старый массив
`questionTypes`, темы, открытые вопросы и персонализация в запросе запрещены.
Смена формы questionTypes → questionType требует согласования потребителей.

Неподдерживаемое значение/неверная форма/лишние поля: 400 VALIDATION_ERROR.
Поддерживаемое, но отключённое значение: 400 GENERATION_OPTION_NOT_AVAILABLE.
Сбой чтения или нарушенная полнота/defaults справочника: 503 GENERATION_CONFIG_UNAVAILABLE.
Скрытого fallback на константы или пустой успешный ответ нет.

Допустимый запрос в этой ветке доходит до 501 GENERATION_NOT_IMPLEMENTED.
Задача не создаётся; проверки владельца/готовности материала, идемпотентность запуска,
очередь, ML и WebSocket ещё не реализованы. До запуска отдельной генерации этот
endpoint полезен для проверки настроек, но не является завершённым TY5-сценарием.

OpenAPI: [backend-api.yaml](../backend/backend-api.yaml); DTO и интерфейсы генерирует
Gradle. Это подготовленный контракт; подтверждение FE/ML другой командой ещё требуется.
Внешние приложенные памятки описывают будущую интеграцию и не означают наличия ML в dev.

## Хранение и снимки запусков

Flyway V4 создаёт generation_options: category + option_value — первичный ключ,
available — доступность, default_slot = 1 — default (иначе NULL), sort_order — порядок.
CHECK ограничивает поддерживаемые коды и запрещает недоступный default;
UNIQUE(category, default_slot) не допускает два default одной категории.
Сервис дополнительно проверяет полноту справочника и наличие одного default в каждой
категории. Справочник читается одним SQL-запросом, чтобы значения и defaults были согласованы.

generation_settings_snapshots хранит фактические значения выбранного запуска.
Это отдельная таблица без ссылок на изменяемый справочник. Repository предоставляет
только insert/read и не перезаписывает строку существующего jobId.
В будущем сервис задач вызывает `GenerationParametersService.saveForRun(jobId, request)`
в короткой транзакции создания задачи; затем использует возвращённый
`SelectedGenerationSettings`. На повторном исполнении нужно читать сохранённый снимок,
а не вновь разрешать настройки по изменившемуся справочнику.
Метод готов и проверяется интеграционным тестом, но в текущем POST не вызывается:
отказ 501 не должен оставлять фиктивный запуск.

Коды в публичном API оставлены в верхнем регистре существующего backend-контракта.
`mlDifficulty()` и `mlQuestionType()` дают easy/medium/hard и
single_choice/multiple_choice/true_false. В ML передаётся один тип, без тем и персонализации.
Когда появится QuizGenerator, BE передаст ему requestId/jobId, materialId, извлечённый
текст и эти настройки; сам промпт и OpenRouter не относятся к справочнику.
M1 v2 / correctOptionIds относятся к результату ML и здесь не изменяются.
Сохранение Quiz и выставление COMPLETED после commit — задача отдельного lifecycle.

## Серверное изменение конфигурации

Новый административный HTTP-интерфейс не создаётся. Ответственный участник с доступом
к серверной БД применяет версионируемый SQL-скрипт через psql после согласования изменения.
Это технический способ изменения; организационное согласование команды остаётся открытым.
Скрипт берёт блокировку от одновременных изменений, проверяет код и default и делает
COMMIT целиком. Ошибка прерывает транзакцию: используется ON_ERROR_STOP=1.
Нельзя менять старую миграцию, запускать seed повторно или править таблицу снимков.

Из корня репозитория (PowerShell), после запуска корневого Compose:

```powershell
# Сделать 5 вопросов новым default.
Get-Content -Raw backend/scripts/update-generation-option.sql | docker compose exec -T exam-ai-db psql -U postgres -d study_assistant_db -v ON_ERROR_STOP=1 -v category=QUESTION_COUNT -v value=5 -v available=true -v make_default=true
# Отключить 10, сохранив строку в справочнике.
Get-Content -Raw backend/scripts/update-generation-option.sql | docker compose exec -T exam-ai-db psql -U postgres -d study_assistant_db -v ON_ERROR_STOP=1 -v category=QUESTION_COUNT -v value=10 -v available=false -v make_default=false
```

Повторите GET из README: defaults.questionCount = 5, у 10 available = false.
POST с 10 даёт 400 GENERATION_OPTION_NOT_AVAILABLE; с 5 проходит проверку настроек
и пока даёт 501. Для независимой проверки defaults сложности/типа используйте
category=DIFFICULTY, value=HARD или category=QUESTION_TYPE, value=TRUE_FALSE.
Отключение действующего default без предварительной замены завершится ошибкой,
как и value=20 или попытка сделать недоступное значение default.

Восстановление начального примера:

```powershell
Get-Content -Raw backend/scripts/update-generation-option.sql | docker compose exec -T exam-ai-db psql -U postgres -d study_assistant_db -v ON_ERROR_STOP=1 -v category=QUESTION_COUNT -v value=10 -v available=true -v make_default=true
```

Уже сохранённые снимки запусков не меняются. Проверяйте сценарий на тестовом
окружении; автоматический интеграционный сценарий ниже использует свою отдельную БД.

## Повторяемые проверки

Из backend: `./gradlew build` (Windows: `.\gradlew.bat build`). Это все unit-тесты,
HTTP-контракт MockMvc, миграции и JDBC в H2, проверки регистрации/входа, OpenAPI и jar.
Профиль test задан явно для Spring-тестов; старая пользовательская PostgreSQL не используется.

`python scripts/check-generation-parameters.py` — реальный PostgreSQL 16, Flyway и
HTTP в одноразовом Compose-проекте. Требует Docker и готового app.jar из build.
Он проверяет JWT/401, seed, три вида default, изменение доступности и ответов API,
неподдерживаемые и отключённые значения, обязательность полей, единственный тип,
откат некорректной правки, снимок и 503 повреждённого справочника.
Порт по умолчанию 18080, переопределение — GENERATION_TEST_PORT.
Контейнеры без фиксированных имён, без публикации PostgreSQL и без томов пользователя;
в finally удаляются только ресурсы уникального проверочного Compose-проекта.
