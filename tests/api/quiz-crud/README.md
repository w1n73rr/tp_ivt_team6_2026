# HTTP-проверка CRUD тестов — tpivt6_26-79

Сценарий проверяет внешнее API приложения. Нужны Docker с Linux-контейнерами, Compose >= 2.24.4 и JDK 17+ для API-тестов. Используйте отдельный локальный стенд: сценарий регистрирует двух пользователей, загружает лекцию и генерирует два теста. Промежуточные данные остаются в тестовой БД до остановки стенда.

## Подготовка и запуск

Из корня проекта создайте отдельный файл `.env.generation.local` из `.env.example`, если его ещё нет. Заполните четыре обязательных секрета по инструкции корневого README. Для отдельного стенда задайте `FRONTEND_PORT=13001`, `BACKEND_PORT=18081`, `POSTGRES_PORT=15433`, `S3_PORT=18333`. Используйте свободные порты. Секреты держите в локальном файле.

```powershell
docker compose -p generation-acceptance --env-file .env.generation.local config --quiet
docker compose -p generation-acceptance --env-file .env.generation.local up -d --wait exam-ai-db seaweedfs
```

Для воспроизводимого прогона с реальными API, PostgreSQL, хранилищем и подменой внешней ML-модели запустите тестовый сервер из корня в отдельном PowerShell с JDK 25. Сервер использует отдельную БД `generation_ui_controlled`. Окно сервера должно оставаться открытым до конца проверки.

```powershell
.\deploy\scripts\start-generation-ui-test-server.ps1
```

В другом PowerShell из корня выполните:

```powershell
$env:API_BASE_URL = 'http://localhost:18083'
$env:API_TEST_DISPOSABLE = 'true'
$env:API_TEST_ML_MODE = 'controlled'
$env:API_TEST_VERSION = (git rev-parse HEAD)
.\tests\ui\gradlew.bat -p .\tests\api test -PquizCrud
```

Для прогона с внешней моделью настройте существующие `OPENROUTER_API_KEY` и `OPENROUTER_MODEL` в локальном файле, соберите и поднимите Backend отдельного стенда командой `docker compose -p generation-acceptance --env-file .env.generation.local up -d --build --wait exam-ai-backend` и укажите `API_BASE_URL=http://localhost:18081` и `API_TEST_ML_MODE=live` в окружении PowerShell. Команда выполнит две генерации. Сбой модели останавливает сценарий с ненулевым кодом и сохраняется в отчёте.

Если Gradle Plugin Portal прерывает TLS-соединение при скачивании плагинов, сохраните в отдельный локальный файл `repositories.gradle` следующий init-скрипт:

```groovy
settingsEvaluated { settings ->
    settings.pluginManagement.repositories {
        clear()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Повторите запуск сервера с параметром `-GradleInitScript` и абсолютным путём к этому файлу. Gradle сначала проверит Maven Central, затем Gradle Plugin Portal.

## Проверки и отчёт

| Проверка | Ожидание |
| --- | --- |
| Генерация и чтение | POST 202, задача COMPLETED, два отдельных теста, GET 200, сохранённые параметры и пять вопросов |
| Список и детали | Только публичные поля, без ключа ответов, объяснений и приватного JSON |
| Без авторизации | 401 AUTHENTICATION_REQUIRED |
| Чужой и отсутствующий ID | GET/PATCH/DELETE 404 QUIZ_NOT_FOUND. Данные владельца сохранены |
| Некорректное название | PATCH 400 для пустого, пробельного и слишком длинного имени и лишних полей, 409 для дубликата |
| Изменение | PATCH 200, повторные GET списка и деталей подтверждают имя, остальные поля неизменны |
| Удаление | DELETE 204 с пустым телом, повторный GET/DELETE 404, второй тест и материал сохранены |

Файл `tests/api/build/reports/quiz-crud-http.json` содержит версию, режим ML, ожидаемый и фактический код HTTP каждого запроса и результаты проверок полей. Тела ответов, пароли и токены туда не записываются. К отчёту задачи приложите этот файл и укажите версию запущенного Backend, ОС и способ запуска. `API_TEST_ML_MODE` помечает режим в отчёте. Для запуска из IDE подключите Gradle-модуль `tests/api`, задайте те же переменные в Run Configuration и запустите `QuizCrudApiTest`. HTML-отчёт JUnit находится в `tests/api/build/reports/tests/test/index.html`. Сценарий дополняет проверки Backend из [QUIZZES.md](../../../backend/QUIZZES.md).

После проверки остановите тестовый сервер через Ctrl+C и остановите отдельный Compose-проект:

```powershell
docker compose -p generation-acceptance --env-file .env.generation.local down
```
