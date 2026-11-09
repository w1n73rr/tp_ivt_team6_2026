# CRUD сохранённых тестов

## Хранение и единый путь записи

CRUD работает с существующей таблицей quizzes (миграция V6). GenerationWorker
проверяет результат ML и вызывает единственный GenerationStore.saveQuiz в короткой
транзакции. В ней сохраняются summary_json, приватный result_json с вопросами,
вариантами, правильными ответами и объяснениями, затем выставляется COMPLETED.
Если запись или обновление статуса не удались, транзакция откатывается целиком.
Новая структурная проверка M1 v2 на границе SavedQuiz.from не даёт сохранить
невалидный исходный Quiz при корректной внешней проекции адаптера.

Другого способа создания тестов, дополнительной таблицы или POST импорта вопросов
нет. HTTP-запуск, извлечение текста, worker и WebSocket сохранены из актуального dev:
подробности в [GENERATION.md](GENERATION.md). Вызов модели выполняется вне SQL-транзакции.
Backend создаёт UUID вопросов и вариантов, сохраняя ссылки правильных ответов.
Время summary и строки quizzes совпадает с микросекундной точностью PostgreSQL.

Добавлена только миграция V8 с индексом поиска имени в пределах владельца.
Применённые V1–V4, V6 и V7 не изменялись. Существующие тесты, их имена и JSON-формат
сохраняются. Имя/формат материала и предмет являются снимками summary_json;
параметры запуска читаются из неизменного generation_jobs.request_json.
Редактирование каталога, переименование или удаление исходного материала не меняют тест.

## API

Все операции требуют Authorization: Bearer <JWT>. Контракт — backend-api.yaml,
реализация использует генерируемый SavedQuizzesApi. Генерация остаётся в QuizzesApi;
разделение тегов не меняет существующие URL и operationId.

| Метод | Путь | Ответ |
| --- | --- | --- |
| POST | /api/quizzes | Существующий запуск генерации, 202 + jobId; нужен Idempotency-Key |
| GET | /api/quizzes | 200, {items:[...]}; только свои тесты, created_at DESC и id DESC |
| GET | /api/quizzes/{id} | 200, {quiz, settings, questions} |
| PATCH | /api/quizzes/{id} | Только {"name":"Новое название"}; 200 с обновлённой строкой |
| DELETE | /api/quizzes/{id} | 204, без тела; приватный результат удалён вместе с тестом |

Строка списка содержит id, name, materialId, materialName, format (PDF/DOCX/TXT),
subjectId, subjectName, kind, difficulty, questionType, фактический questionCount и createdAt.
Detail дополнительно содержит снимок settings (запрошенное количество, сложность, тип)
и публичные UUID/тексты/позиции вопросов и UUID/тексты вариантов.
Правильные ответы, explanation и ML-local sourceId не выдаются ни в списке, ни в detail.
Публичные вопросы формируются отдельной проекцией; приватный JSON напрямую не сериализуется.
Одно SQL-чтение даёт согласованный снимок при конкурентном удалении. GET имеет Cache-Control: no-store.

Правила переименования соответствуют текущему FE: trim как в JavaScript (включая
неразрывные пробелы и BOM), затем 1–100 символов UTF-16. Точный дубликат другого
своего теста даёт 409; регистр учитывается, собственное прежнее имя допустимо.
Имена разных владельцев независимы. Уже существующие повторяющиеся имена и принятая
политика имён при генерации сохраняются; уникальный индекс задним числом не вводится.
Для PATCH допустимы до 4000 входных символов, чтобы сначала обрезать пробелы;
после нормализации сервис строго ограничивает длину 100. Другие поля в PATCH запрещены.

| Ситуация | HTTP / code |
| --- | --- |
| Без действительного JWT | 401 AUTHENTICATION_REQUIRED |
| Чужой или отсутствующий тест, включая повторное DELETE | 404 QUIZ_NOT_FOUND |
| Пустое или слишком длинное нормализованное имя | 400 INVALID_QUIZ_NAME |
| Ошибка JSON/DTO/UUID, лишнее поле в PATCH | 400 VALIDATION_ERROR |
| Имя занято другим своим тестом | 409 QUIZ_NAME_ALREADY_EXISTS |
| Ошибка БД или commit CRUD | 503 QUIZ_STORAGE_UNAVAILABLE |

Rename и delete сериализуются блокировкой владельца и применяют SQL-предикат user_id.
Переименование меняет только name в summary_json. result_json, настройки, ID и дата
остаются прежними. DELETE удаляет всю запись с вложенными вопросами/вариантами;
отдельных дочерних SQL-записей этого формата нет, поэтому сирот не возникает.
Материал, S3-файл, generation_jobs и generation_settings_snapshots сохраняются.
COMPLETED остаётся историческим результатом задачи; после удаления её quizId даёт 404.
Идемпотентный повтор задачи не должен заново создавать удалённый тест.

## Самостоятельный запуск

Нужны Docker с Linux engine и Compose >= 2.24.4. В новой копии с новой БД скопируйте
.env.example в .env, заполните POSTGRES_PASSWORD, JWT_SECRET, S3_ACCESS_KEY,
S3_SECRET_KEY четырьмя независимыми случайными значениями, как описано в README.
Существующий .env не перезаписывайте. Из корня репозитория:

```powershell
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

Сайт — http://localhost:3000, Swagger — http://localhost:8080/swagger-ui.html.
Для реальной модели задайте серверный OPENROUTER_API_KEY и поддерживаемый OPENROUTER_MODEL.
Без ключа CRUD работает с уже сохранёнными тестами; генерация завершится NOT_CONFIGURED.
Новых runtime-переменных окружения CRUD не добавляет. Ключи в frontend не передаются.

API-проверка в PowerShell:

```powershell
$base = 'http://localhost:8080'
$suffix = [guid]::NewGuid().ToString('N').Substring(0,8)
$body = @{username="quiz_$suffix"; email="quiz_$suffix@example.test"; password='local-test-password-123'} | ConvertTo-Json
$auth = Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $body
$headers = @{Authorization="Bearer $($auth.token)"}
Invoke-RestMethod "$base/api/quizzes" -Headers $headers | ConvertTo-Json -Depth 8
```

У нового пользователя ожидается {"items":[]}, без токена — 401. Загрузите TXT/PDF/DOCX
и запустите генерацию по [GENERATION.md](GENERATION.md), дождитесь COMPLETED.
Возьмите quizId из задачи или списка:

```powershell
$quizId = 'UUID-из-списка'
Invoke-RestMethod "$base/api/quizzes/$quizId" -Headers $headers | ConvertTo-Json -Depth 8
Invoke-RestMethod "$base/api/quizzes/$quizId" -Method Patch -Headers $headers -ContentType 'application/json' -Body '{"name":"Новое название"}'
Invoke-RestMethod "$base/api/quizzes/$quizId" -Headers $headers | ConvertTo-Json -Depth 8
Invoke-RestMethod "$base/api/quizzes/$quizId" -Method Delete -Headers $headers
# Повторные GET и DELETE должны дать 404.
```

Зарегистрируйте второго пользователя и повторите GET/PATCH/DELETE первого ID с его
токеном: каждый запрос должен дать 404. PATCH с пустым именем/101 символом — 400,
с полем difficulty — 400, с занятым именем другого своего теста — 409.
FE уже читает настоящий список; методы rename/remove HTTP-адаптера FE реализуются
отдельно, поэтому эти операции проверяйте напрямую через API.

## Проверки без внешнего LLM

Нужны JDK 25 и запущенный Docker Desktop. Одна команда из корня:

```powershell
.\backend\scripts\check-quiz-crud.ps1
```

Скрипт создаёт PostgreSQL 16 с уникальным именем и случайным localhost-портом,
задаёт процессные TEST_DATABASE_* и ML_LIVE_TEST=false, выполняет полный
build --rerun-tasks и в finally удаляет только свой контейнер/его одноразовые данные.
Прежние значения окружения восстанавливаются, пользовательская БД не подключается.

QuizCrudIntegrationTest использует настоящий PostgreSQL, backend HTTP/JWT,
POST генерации и реальный QuizGenerator/QuizGeneratorAdapter с HTTP/SSE-клиентом.
Провайдер модели заменён локальным детерминированным HTTP-сервером, S3 изолирован.
Результат передаётся в существующий GenerationWorker.accept, который проверяет его,
вызывает единственный saveQuiz и атомарно выставляет COMPLETED. Вопросы не вставляются
тестом напрямую в quizzes. Это проверка интеграции и записи, а не качества живой модели.
Сценарий сбоя блокирует обновление COMPLETED SQL-ограничением и проверяет откат уже
выполненного INSERT теста: фиктивного успеха и частичного результата нет.

Unit-тесты CRUD без БД, из backend:

```powershell
.\gradlew.bat test --tests 'tech.genna.TY3.service.quiz.*Test' --tests 'tech.genna.TY3.controller.QuizControllerTest' --tests 'tech.genna.TY3.service.generation.SavedQuizTest'
```

Linux/CI: отдельный PostgreSQL, TEST_DATABASE_URL, TEST_DATABASE_USERNAME,
TEST_DATABASE_PASSWORD, затем `ML_LIVE_TEST=false ./gradlew build` из backend.
Только интеграция: `./gradlew test --tests 'tech.genna.TY3.ml.QuizCrudIntegrationTest' --rerun-tasks`.
JUnit XML — build/test-results/test/TEST-*.xml, HTML — build/reports/tests/test/index.html.
Текущий CI уже запускает этот набор вместе с остальными backend-тестами.
Человеческое ревью, согласование аналитики/FE и зелёный удалённый CI требуют отдельных
подтверждений; фактическое состояние указано в [QUIZZES-REPORT.md](QUIZZES-REPORT.md).
