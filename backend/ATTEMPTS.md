# Попытки: запуск, продолжение и сохранение ответов

Запуск проекта: инструкция в корневом README (`.env.example`, затем
`docker compose up -d --build --wait`). Дополнительных переменных нет.
Flyway V9 создаёт отдельные таблицы на чистой тестовой БД; проверки не используют
и не изменяют прежнюю пользовательскую БД.

Все запросы требуют `Authorization: Bearer <токен входа>`. Получите токен через
`POST /api/auth/register` или `/api/auth/login`, загрузите материал и сгенерируйте
тест по [инструкции генерации](GENERATION.md). Используйте `quizId` сохранённого теста.

1. `POST /api/quizzes/{quizId}/attempts`, заголовок `Idempotency-Key: <новый UUID>`.
   Ответ 201: `attempt.id`, вопросы с вариантами и пустые ответы. Повтор ключа
   возвращает ту же попытку; новый ключ создаёт повторное прохождение.
2. `PUT /api/attempts/{attemptId}/answers/{questionId}` с JSON
   `{"selectedOptionIds":["<UUID варианта из этого вопроса>"]}`.
   Ответ 200: questionId, selectedOptionIds, savedAt; оценки и ключа нет.
3. Повторите PUT с другим вариантом. `GET /api/attempts/{attemptId}` должен
   вернуть заменённый выбор, без дублей. Повтор того же PUT сохраняет savedAt.
4. PUT `{"selectedOptionIds":[]}` очищает выбор. SINGLE_CHOICE и TRUE_FALSE
   допускают максимум один выбранный вариант; MULTIPLE_CHOICE — подмножество
   вариантов. Пустой набор допустим для любого типа.
5. Вариант другого вопроса → 400 без изменения ответа. Чужой/отсутствующий
   questionId в своей попытке → 404. Чужая/отсутствующая попытка → одинаковый
   404; без входа → 401; закрытая попытка → 409.

Снимок вопросов, приватного ключа и названия создаётся при запуске. Удаление
исходного теста не удаляет попытку. GET и PUT возвращают только публичную
проекцию. Все SQL используют параметры. PUT блокирует строку попытки до commit;
некорректный запрос не пишет состояние. Закрытие использует ту же блокировку.

## Завершение

`POST /api/attempts/{attemptId}/complete`, без тела, возвращает AttemptCompletion:
балл `score` (= correctCount), процент, totalQuestions, correctCount, incorrectCount,
skippedCount, durationSeconds, completedAt и снимок данных теста. Счётчики раздельны:
correctCount + incorrectCount + skippedCount = totalQuestions. Пропуск даёт 0 баллов,
но не увеличивает incorrectCount. Процент = 100 * score / totalQuestions, HALF_UP
до двух знаков. Только полное совпадение множеств даёт 1 балл для всех трёх типов.

Итог берётся только из серверного снимка и сохранённых ответов. Поля входящего
тела не влияют на балл/статус. INSERT/UPDATE результата, время и COMPLETED — один
UPDATE и одна транзакция; CHECK V10 запрещает частичный итог. PUT ответа и complete
берут одну row lock: сначала записанный ответ войдёт в итог, после complete PUT
получит 409. Повтор complete возвращает сохранённые значения без пересчёта.
ABANDONED → 409, чужая/отсутствующая попытка → одинаковый 404, нет входа → 401.
Complete не возвращает ключи или сравнение по вопросам.

Ручная проверка: после PUT выполните complete дважды, сравните JSON, попробуйте
ещё один PUT (409). В новой попытке оставьте ответ пустым: correctCount=0,
incorrectCount=0, skippedCount=totalQuestions. Используйте токен второго аккаунта
для complete (404) и уберите Authorization (401).

AttemptScorerTest покрывает три типа, множества в разном порядке, лишние/недостающие
варианты, пропуски, неверные ответы и округление. AttemptCompletionIntegrationTest
проверяет настоящий HTTP/PostgreSQL: повтор, закрытую запись, двух пользователей,
rollback при отказе БД и конкурентный запуск PUT/complete с общей стартовой защёлкой.

## Проверки

Unit без БД: `./backend/gradlew -p backend test --tests '*AttemptServiceTest'`.
Полная сборка и реальный PostgreSQL, изолированный от рабочей БД:
`powershell -File backend/scripts/check-quiz-crud.ps1` (JDK 25, Docker Desktop).
Скрипт запускает `build --rerun-tasks` и удаляет только свой временный контейнер.
Для CI — переменные TEST_DATABASE_* из [TESTING.md](TESTING.md), затем
`./backend/gradlew -p backend build`.

AttemptServiceTest проверяет три типа, замену/очистку/повтор, чужие ID, закрытый
статус, отсутствие ключей, отказ БД. AttemptIntegrationTest использует настоящий
HTTP/JWT/PostgreSQL: идемпотентный запуск, повтор, запись/чтение, запреты и снимки.
S3 изолирован; LLM не вызывается, исходный тест — контролируемая fixture.

Для приёмки отдельно приложить PR, зелёный CI проверяемого SHA, человеческий
аппрув и подтверждение повторения README другим участником. Локальный запуск
не заменяет эти подтверждения. Ветка предназначена для ручного PR пользователем.

Проверка именно Compose-стенда после сборки: `python backend/scripts/check-attempts.py`.
Используется отдельный проект Compose, PostgreSQL, SeaweedFS и собранный app.jar;
временные данные удаляются в finally. Порт по умолчанию 18080 ($env:GENERATION_TEST_PORT).
