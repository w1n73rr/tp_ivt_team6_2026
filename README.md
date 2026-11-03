# tp_ivt_team6_2026

Учебный сервис генерации тестов по материалам. Backend: Java 25, Spring Boot 4,
PostgreSQL 16, Flyway. Frontend: Angular. Файлы: S3-совместимый SeaweedFS.

Реализованы регистрация/вход, предметы, теги, материалы и справочник параметров
генерации. Запуск ML, очередь задач и WebSocket в этой ветке пока не подключены.
Наличие endpoint в Swagger не означает, что он реализован: остальные заготовки дают 501.

## Первый запуск

Нужны Git и Docker с Compose v2 (на Windows — запущенный Docker Desktop).
Для локальной сборки и тестов дополнительно нужны JDK 25; Gradle 9.1.0 скачает Wrapper.
Для проверки реальной интеграции нужен Python 3.10+ без дополнительных пакетов.

Из корня репозитория:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 exam-ai-backend
```

При повторном запуске достаточно `docker compose up -d`. Порты 3000, 8080, 5432
и 8333 должны быть свободны. Backend ожидает готовности PostgreSQL и SeaweedFS;
первый запуск дольше из-за скачивания образов/зависимостей. Схема и начальные данные
создаются автоматически Flyway. Не изменяйте уже применённые миграции.

- Приложение: http://localhost:3000 — зарегистрируйтесь или войдите.
- Swagger: http://localhost:8080/swagger-ui.html
- Контракт: http://localhost:8080/backend-api.yaml

`.env.example` содержит только локальные примеры. `.env` игнорируется Git.
Для прямого `bootRun` задайте переменные DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET,
JWT_EXPIRATION из примера и S3_ENDPOINT=http://localhost:8333 в окружении процесса.
Справочник генерации не требует ключа OpenRouter или новых переменных окружения.

Остановка: `docker compose down`. Данные остаются в томах; не используйте `down -v`,
если хотите сохранить пользователей и материалы.

## Как проверить задачу «API параметров генерации»

В PowerShell из корня репозитория:

```powershell
$base = 'http://localhost:8080'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$registration = @{username="student_$suffix"; email="student_$suffix@example.test"; password='local-password-123'} | ConvertTo-Json
$auth = Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $registration
$headers = @{Authorization="Bearer $($auth.token)"}
$params = Invoke-RestMethod "$base/api/generation/parameters" -Headers $headers
$params | ConvertTo-Json -Depth 6
```

Ожидается HTTP 200: `questionCounts` со значениями 5/10, три `difficulties`, три
`questionTypes`, `available` у каждого значения, `defaults` из БД и
`questionTypeSelection: SINGLE`. Начальные доступности — true. Defaults подтверждены пользователем 03.11.2026:
10 / MEDIUM / SINGLE_CHOICE.
Без токена — HTTP 401. Ответ имеет Cache-Control: no-store.

Проверка запрета 20 вопросов (PowerShell покажет HTTP 400):

```powershell
$headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
$body = @{materialId=[guid]::NewGuid().ToString(); questionCount=20; difficulty='MEDIUM'; questionType='SINGLE_CHOICE'} | ConvertTo-Json
Invoke-RestMethod "$base/api/quizzes" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

Для допустимых настроек POST сейчас возвращает 501 GENERATION_NOT_IMPLEMENTED:
он проверяет справочник, но ещё не создаёт задачу и не вызывает ML.
Ровно один тип передаётся строкой `questionType`; массив `questionTypes`, темы,
открытые вопросы и персонализация запрещены.

Изменение доступности/defaults и повторная проверка по шагам:
[docs/generation-parameters.md](docs/generation-parameters.md).
Там же описаны формат ответа, граница ML, снимки настроек запусков и приёмка.

## Сборка и проверки

Все тесты изолированы от прежней пользовательской БД. Unit-тесты используют mock
зависимостей; тесты Spring/JDBC — отдельную H2 в памяти с профилем test и Flyway.

```powershell
cd backend
.\gradlew.bat build
python scripts/check-generation-parameters.py
```

В Linux/CI: `cd backend && ./gradlew build`, затем
`python3 scripts/check-generation-parameters.py` при доступном Docker.
`build` включает валидацию OpenAPI, генерацию Java, все тесты и сборку jar.
Unit-тесты новой логики и HTTP-контракт без запуска БД:

```powershell
.\gradlew.bat test --tests 'tech.genna.TY3.service.generation.GenerationParametersServiceTest' --tests 'tech.genna.TY3.controller.GenerationParametersControllerTest'
```

Python-сценарий поднимает отдельные PostgreSQL 16 и backend на 127.0.0.1:18080,
проверяет настоящие HTTP-запросы, сохранённые изменения справочника, отклонение
запрещённых параметров и неизменность снимка. После проверки удаляет только свои
контейнеры/тома. При занятом порте задайте GENERATION_TEST_PORT=18081 в окружении.
Используется уже собранный `backend/build/libs/app.jar`; корневой Compose и его БД
не затрагиваются. Результаты Gradle: `backend/build/reports/tests/test/index.html`.

Контракт и ограничения: [backend/API.md](backend/API.md).
Материалы: [backend/MATERIALS.md](backend/MATERIALS.md).
Отчёт по этой задаче: [docs/generation-parameters-report.md](docs/generation-parameters-report.md).
