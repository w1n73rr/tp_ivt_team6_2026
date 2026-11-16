# Проверка доступа к материалам, тестам, попыткам и результатам

Все перечисленные HTTP-маршруты проходят SecurityFilterChain и требуют JWT.
Запросы без входа, включая изменяющие запросы без/с некорректным телом, дают 401
до чтения объекта. CurrentUser берёт владельца из principal, а не JSON/query.
Для существующего чужого и отсутствующего объекта используются одинаковые
owner-scoped запросы и безопасные 404. Cache-Control: no-store применяется ко
всему /api/, включая ошибки.

| Маршруты чтения / записи | Контроль и проверка |
| --- | --- |
| GET/POST /materials; GET/PATCH/DELETE /materials/{id} | MaterialRepository фильтрует user_id; запись блокирует владельца/объект. MaterialServiceTest, AccessControlIntegrationTest: реальный материал двух пользователей, чужой/отсутствующий ID, 401 |
| GET /materials/{id}/file и /download | Сначала owner-scoped материал, затем S3 namespace владельца. AccessControlIntegrationTest проверяет отказ до S3 |
| POST /files; GET/DELETE /files/{id} | Legacy S3 namespace GENERAL строится из principal UUID + file UUID, не из клиентского пути. GENERAL отделён от MATERIALS; чужой объект не адресуется. Удаление отсутствующего ключа идемпотентно. Проверен код, HTTP 401; S3 в unit/integration изолирован |
| GET/POST /quizzes; GET/PATCH/DELETE /quizzes/{id} | QuizRepository и GenerationStore фильтруют владельца. QuizServiceTest, QuizCrudIntegrationTest, AccessControlIntegrationTest: чтение/изменение/удаление, 401/404, отсутствие ключей |
| GET /generation-jobs/{id} | Owner в SQL; QuizCrudIntegrationTest и AccessControlIntegrationTest. Чужой/отсутствующий job → одинаковый 404 |
| /ws/generation | Handshake публичен, но SUBSCRIBE требует JWT и owner-scoped job; повторная подписка закрывается. JWT перепроверяется при доставке. Проверен код; WebSocket transport этим HTTP-набором не проверяется |
| POST /quizzes/{id}/attempts; GET /attempts/{id}; PUT /attempts/{id}/answers/{questionId} | Снимок только своего теста; AttemptRepository фильтрует user_id. Вложенные questionId/optionIds проверяются в снимке. AttemptServiceTest, AttemptIntegrationTest, AccessControlIntegrationTest |
| POST /attempts/{id}/complete; GET /attempts/{id}/result | Complete реализован: owner-scoped row lock, только IN_PROGRESS/повтор COMPLETED; AttemptCompletionIntegrationTest. GET result пока 501; anonymous 401. Проверки результата добавляются зависимой веткой result |
| GET /attempts, POST /attempts/{id}/abandon, /review, /hint, /explanation | Заглушки 501, не читают БД и не возвращают ключей/оценок. /review удаляется следующей веткой result; подсказки и история вне этих задач |
| /materials/bulk-delete, /processing, /processing/retry, /topics | Заглушки generated interface: 501 после авторизации, не имеют обхода хранения |

Маршруты с префиксом `/api`. Остальные заглушки контракта (analytics, training,
preferences) зарегистрированы, но не реализованы; их наличие не означает готовность
соответствующей функции. GeneratedApiContractTest проверяет регистрацию маршрутов.

Исправления этой ветки: токен удалённого пользователя обрабатывается как отсутствие
сессии; ответы /api не кешируются; исключения SQL/S3 не печатаются целиком в
контроллере, фоновом сохранении и очистке файлов. В логах остаются коды, тип ошибки
и технические UUID. SafeErrorsTest проверяет отсутствие содержимого исключений и
stack trace; JwtAuthFilterTest — отвергнутый JWT и удалённого пользователя.
Отладочное логирование Hibernate выключено. Зависимости не изменялись.

SQL JDBC использует `?`; JPA использует типизированные методы с владельцем.
Единственная динамическая часть нового SQL — серверный фиксированный `FOR UPDATE`.
Проверки DTO не принимают owner/status/score/ключ ответа; неизвестные поля
отклоняются общей Jackson-конфигурацией. Публичные проекции не копируют приватный
JSON результата ML. Вопрос/вариант из другого снимка отклоняется до UPDATE.

Воспроизведение: корневой README и [ATTEMPTS.md](ATTEMPTS.md).
Полный локальный набор: `powershell -File backend/scripts/check-quiz-crud.ps1`;
реальный Compose/JAR: `python backend/scripts/check-attempts.py` после сборки.
Используются одноразовые БД, прежняя пользовательская БД не нужна.
Новых env-параметров и зависимостей нет. Для закрытия приложить CI SHA, PR,
аппрув ревьюера именно контроля доступа и подтверждение повторения другим участником.
