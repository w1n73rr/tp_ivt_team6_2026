# tp_ivt_team6_2026

Учебный монорепозиторий: Angular, Spring Boot, PostgreSQL и SeaweedFS (S3).
Сфера — источник кода и место ревью; GitHub — зеркало для Actions и реестра GHCR.

## Структура

| Каталог | Назначение |
| --- | --- |
| `backend/` | Сервер, миграции, встроенные тесты, контракт и описание реализации |
| `frontend/` | Интерфейс, компонентные тесты, nginx |
| `tests/api/` | Внешние HTTP-тесты материалов |
| `tests/ui/` | Браузерные тесты и их сценарии |
| `tests/checks/` | Smoke и отдельная проверка реальной генерации через API |
| `.github/workflows/` | Задания GitHub Actions |
| `.github/scripts/` | Выбор заданий по изменённым файлам и тесты этих правил |
| `deploy/compose/` | Дополнения Compose для CI, реестра, сервера и тестовых стендов |
| `deploy/env/` | Только пример одноразового окружения CI |
| `deploy/scripts/` | Локальные команды запуска проверок |

## Локальный запуск

Нужны Docker с Linux-контейнерами и Compose >= 2.24.4. Команды выполняются из корня.
Для новой базы скопируйте `.env.example` в `.env`, если своего `.env` ещё нет.
Заполните `POSTGRES_PASSWORD`, `JWT_SECRET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`
четырьмя независимыми случайными значениями (например, `openssl rand -hex 32`).
Остальные настройки и пояснения находятся в шаблоне. Не перезаписывайте существующие секреты.

```bash
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

Сайт: http://localhost:3000. Порты можно изменить через `.env`.
Остановка: `docker compose down`. Постоянные volumes сохраняются;
`down -v` удаляет данные. Flyway применяет миграции при запуске backend:
перед обновлением существующей базы нужны резервная копия и проверка совместимости.
Номера портов не являются секретами; защиту обеспечивает ограничение доступа.

Для настоящей генерации дополнительно задайте `OPENROUTER_API_KEY` и точный
`OPENROUTER_MODEL`. Ключ передаётся только backend. Без него генерация завершается
ошибкой `NOT_CONFIGURED`; остальные функции можно проверять отдельно.
Никогда не помещайте реальные секреты в Git, Dockerfile, frontend или отчёты.
`config --quiet` проверяет конфигурацию без печати значений секретов.

## Проверки и CI

```bash
node --test .github/scripts/changes.test.mjs tests/checks/generation-check.test.mjs
npm --prefix frontend ci
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
```

Backend требует JDK 25. Его интеграционным тестам нужен отдельный PostgreSQL:
задайте `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`,
затем выполните `./backend/gradlew -p backend test bootJar`. Рабочую БД не используйте.
Подготовка и ограничения: [тестирование backend](backend/TESTING.md).
Внешние наборы: [API](tests/api/README.md), [UI](tests/ui/README.md).

Каждый push в GitHub запускает поиск секретов Gitleaks и выбор проверок.
Тип ветки (`feature`, `fix`) не определяет набор: важны изменённые пути и целевая ветка.

| Изменение | Проверки |
| --- | --- |
| Только корневой README в feature-ветке | Gitleaks и выбор этапов |
| Код frontend / backend в feature-ветке | Тесты и сборка соответствующего компонента |
| Compose, Dockerfile, контракт, CI, общие или неизвестные пути | Оба компонента, Compose и smoke |
| Браузерные тесты | Оба компонента, Compose, smoke и браузер |
| Push `dev` | Оба компонента, Compose и smoke |
| Push `master` | Оба компонента, Compose, smoke и браузер; после успеха — публикация |
| Ручной запуск с `full=true` | Оба компонента, Compose, smoke и браузер |

Каждое задание получает отдельную временную машину Ubuntu 24.04. Frontend собирается
и тестируется непосредственно на машине; backend использует отдельный PostgreSQL-сервис.
Задание интеграции получает готовые JAR и Angular-файлы как артефакты этого же запуска,
упаковывает их в Docker и поднимает одноразовый Compose. Smoke проверяет регистрацию,
вход, защиту API и операции с файлами. Браузерный набор работает с поднятым приложением;
для управляемых сценариев генерации есть отдельные backend, БД и S3 без внешней модели.
Контроллер управления тестами не попадает в production JAR.

Обычный CI исключает только тег `live-ml`. Реальная модель не вызывается на каждый push.
Полный ручной прогон: Actions → «Проверка проекта» → Run workflow → нужная ветка,
`full=true`, `negative_smoke=false`. Ручной запуск не публикует образы.
`negative_smoke=true` намеренно ломает адрес API для контрольного красного прогона.
Новый запуск той же ветки отменяет предыдущий через `concurrency`.
Отчёты сохраняются на 7 дней, сборки — на 1 день.

## Отдельная проверка настоящей модели

Workflow «Реальная генерация DEV» запускается вручную только на `dev`.
GitHub Environment `DEV`: разрешена ветка `dev`, настроен ответственный reviewer,
в secrets находится `OPENROUTER_API_KEY`, в variables — `OPENROUTER_MODEL`.
Используйте отдельный ключ CI с ограниченным бюджетом, не серверный ключ.
Если запуск и подтверждение делает один человек, Prevent self-review должен быть выключен.

После подтверждения workflow поднимает одноразовый стенд, а JavaScript-клиент вызывает
API: регистрация → загрузка лекции → запуск → ожидание `COMPLETED` → проверка
сохранённого теста, повторного чтения, идемпотентности и доступа владельца.
Проверяется пять вопросов MEDIUM / SINGLE_CHOICE. Chrome не запускается.
Отчёт содержит безопасные идентификаторы, статус, код ошибки и длительность.
Стенд затем удаляется; на ВМ ничего не выкладывается.
Успех доказывает интеграцию и сохранение, но не гарантирует смысловое качество вопросов.
Квота, сеть и некорректный ответ модели могут сделать этот прогон красным.

## Приёмка и реестр образов

Feature-ветку отправляйте в Сферу и GitHub. К PR и задаче прикладывайте успешный CI
именно проверяемого коммита. После человеческого ревью и слияния в Сфере передайте
принятый `dev` в зеркало:

```bash
git fetch origin
git push github origin/dev:refs/heads/dev
```

Готовый релиз принимается отдельным PR `dev → master` в Сфере. После ревью и merge:

```bash
git fetch origin
git push github origin/master:refs/heads/master
```

Не используйте force-push. Само наличие двух remote не синхронизирует репозитории.
В приёмке фиксируются PR, человеческий аппрув и комментарий ревьюера, SHA и CI.
После проверки принятой версии завершённую feature-ветку можно удалить.

Только успешный push-CI `master` публикует backend и frontend:
`ghcr.io/w1n73rr/tp_ivt_team6_2026-backend` и `ghcr.io/w1n73rr/tp_ivt_team6_2026-frontend`.
Оба получают `sha-<полный SHA>` и подвижный тег `master`.
Для выкладки выбирайте одинаковый SHA обоих образов. Только задание публикации имеет
`packages: write` и использует временный `GITHUB_TOKEN`.
PostgreSQL и SeaweedFS берутся из сторонних образов, поэтому собственных пакетов для них нет.

## Учебный сервер

Наружу публикуется nginx:80; backend, PostgreSQL и S3 доступны внутри Docker.
Nginx маршрутизирует HTTP-запросы, но не заменяет авторизацию backend и сетевые ограничения.
В облаке разрешите HTTP 80 и SSH 22 только с административных адресов.
HTTP не шифрует трафик: до HTTPS используйте тестовые данные и отдельные пароли.

Разместите конфигурацию одной принятой версии в `/opt/tpivt`, сохранив структуру:
`docker-compose.yml`, `deploy/compose/registry.yml`, `deploy/compose/server.yml`, `.env.example`.
Серверный `.env` создайте отдельно с правами `600` и новыми случайными секретами.
Добавьте `IMAGE_TAG=sha-<полный SHA принятого релиза>` и `SERVER_ORIGIN=http://<публичный-IP>`.
Убедитесь, что оба образа уже опубликованы. Из `/opt/tpivt`:

```bash
sudo docker compose --env-file .env -f docker-compose.yml -f deploy/compose/registry.yml -f deploy/compose/server.yml config --quiet
sudo docker compose --env-file .env -f docker-compose.yml -f deploy/compose/registry.yml -f deploy/compose/server.yml pull
sudo docker compose --env-file .env -f docker-compose.yml -f deploy/compose/registry.yml -f deploy/compose/server.yml up -d --no-build --wait --wait-timeout 300
sudo docker compose --env-file .env -f docker-compose.yml -f deploy/compose/registry.yml -f deploy/compose/server.yml ps
curl -fsS http://127.0.0.1/ >/dev/null
```

Перед выполнением удалите старую строку `COMPOSE_FILE` из серверного `.env` либо обновите
её на `docker-compose.yml:deploy/compose/registry.yml:deploy/compose/server.yml`.
Не меняйте имя проекта `tpivt-server`: его volumes — `tpivt-server_pgdata` и
`tpivt-server_seaweeddata`. Не удаляйте их при обновлении. Смена пароля в `.env`
не меняет пароль уже созданной базы. Никогда не подключайте серверу `deploy/compose/ci.yml`:
там данные временные. Лимиты памяти/журналов и `restart: unless-stopped` заданы в server.yml.
Статус healthy нужно дополнить проверкой входа, файлов и генерации через интерфейс.
Пишущие тестовые сценарии не запускайте против стенда с пользовательскими данными.

Резервные копии вне ВМ, восстановление, автоматическое обновление, откат и HTTPS
ещё требуют настройки. Возврат старого образа не откатывает миграции БД.
До обновления существующих данных нужна проверенная резервная копия.

## Описание реализации

- [API backend](backend/API.md), [материалы и S3](backend/MATERIALS.md).
- [Генерация и сохранение](backend/GENERATION.md), [справочник параметров](backend/GENERATION-PARAMETERS.md).
- [ML](backend/ML.md), [промпты](backend/ML-PROMPTS.md), [ограничения качества](backend/ML-QUALITY.md).
- [Frontend](frontend/README.md), [интеграция генерации](frontend/GENERATION.md).
- [Сценарии генерации UI](tests/ui/generation/README.md).

Исторические отчёты ручных прогонов не являются доказательством проверки текущего SHA.
В репозитории сохранены контракты, ограничения и сценарии; результаты новой версии
смотрите в артефактах соответствующего запуска Actions.
