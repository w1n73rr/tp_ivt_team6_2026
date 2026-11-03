# ML-модуль генерации тестов

Java 25 / Spring Boot 4. Backend вызывает `QuizGenerator.generate(GenerationRequest, Consumer<GenerationEvent>)` на фоновом worker. Отдельного ML-сервера и локальных весов модели нет. Публичный метод получает текст, не путь к файлу. Извлечение и сохранение — backend.

## Вход и выход

`GenerationRequest`: requestId, materialId, text, questionCount (5/10), difficulty (easy/medium/hard), questionType (single_choice/multiple_choice/true_false). Идентификаторы копируются из backend, текст ограничен `ML_MAX_INPUT_CHARS`.

Метод блокирующий; события вызывают быстрый синхронный callback. Доставка в браузер и долговременный статус — backend. События: PREPARING_PROMPT, WAITING_FOR_MODEL, RECEIVING_RESPONSE, VALIDATING, REVIEWING_CONTENT, REVISING, VALIDATED или FAILED. VALIDATED означает проверку ML, а не сохранение. Backend выставляет COMPLETED только после записи.

M1 v2: `Quiz{schemaVersion:2,materialId,title,questions}`. Вопрос: `id,type,text,options[{id,text}],correctOptionIds[],explanation`.

- single_choice: четыре варианта a–d, один правильный.
- multiple_choice: четыре варианта, два или три правильных.
- true_false: true/«Верно», false/«Неверно», один правильный; в серии встречаются оба ответа.

Формальная схема ответа модели: `backend/src/main/resources/ml/result-v2.schema.json`. Модель возвращает оболочку requestId/status/quiz либо диагностируемый INSUFFICIENT_MATERIAL; Java-метод возвращает Quiz или GenerationFailure. Поля ответа модели проверяются строго, неизвестные поля, дубли JSON-ключей и дополнительные объекты отклоняются. Проверяются точное количество, тип, уникальность ID/вариантов и ссылки ответов. Программа перемешивает варианты, сохраняя ключи.

## Модель и промпты

По умолчанию `nvidia/nemotron-3.5-super-vl:free`, OpenRouter Chat Completions. Клиент разрешает только явные модели с суффиксом `:free`. Платного fallback и автоматического повтора сетевых ошибок нет. Ключ — OPENROUTER_API_KEY только на сервере; никогда в frontend, коде и логах.

`src/main/resources/ml/prompt.txt` и `review-prompt.txt` содержат английские инструкции; материал может быть на любом языке, результат на русском. Выбранный тип и форма примера подставляются программой. История других генераций не отправляется. Для medium требуется различение/применение; для hard — совместное применение нескольких правил к одному решению. Инструкции внутри материала не выполняются, пропавшие формулы не восстанавливаются догадкой.

Клиент собирает SSE-поток, игнорирует keepalive/служебные кадры, требует корректного завершения. Процент готовности по токенам не вычисляется. Неполный/обрезанный ответ не считается успехом.

Обычный цикл — генерация и проверка содержания (два обращения). После завершённого невалидного кандидата или конкретных замечаний допускается одна исправляющая генерация. Для каждого полностью полученного, но невалидного JSON вердикта допускается одна повторная проверка того же кандидата по исходнику. Невалидный вердикт не означает pass. Максимум — шесть обращений, общий дедлайн 240 с не увеличен. Таймауты, 429 и сетевые отказы не повторяются автоматически.

Проверяющая модель тоже ошибается. Формальная валидация не доказывает качество, отсутствие смысловых дублей, охват и соблюдение сложности. Известные дефекты hard и фактические прогоны записаны в `docs/ml-prompt-quality-2026-11-03.md`.

## Настройки и запуск

Все серверные параметры перечислены в корневом `.env.example`: OPENROUTER_MODEL, OPENROUTER_API_KEY, OPENROUTER_TIMEOUT_SECONDS (90), OPENROUTER_MAX_OUTPUT_TOKENS (12000), OPENROUTER_REASONING_ENABLED (false), ML_TOTAL_TIMEOUT_SECONDS (240), ML_MAX_INPUT_CHARS (60000). Для Compose также заполните обязательные секреты БД/JWT/S3 по README; прежние пользовательские volumes не удалять и не подключать к экспериментам.

`docker compose up --build` из новой копии с заполненным `.env`. Запуск API/статусы/WebSocket описаны в [GENERATION.md](GENERATION.md). Секрет провайдера не нужен тестировщику общего стенда: он получает собственный JWT штатным входом.

Локальные unit-тесты из backend:

```text
./gradlew test --tests 'tech.genna.TY3.ml.*' --tests 'tech.genna.TY3.service.generation.*' --tests 'tech.genna.TY3.controller.GenerationControllerTest' bootJar
```

На Windows — `./gradlew.bat`. Unit-тесты не вызывают внешний API; полный набор тестов проекта требует отдельной тестовой PostgreSQL по README.

Ручной бесплатный прогон из корня в PowerShell:

```text
./scripts/ml-live.ps1 -Type single_choice -Count 5 -Difficulty medium -Model nvidia/nemotron-3.5-super-vl:free
```

Ключ берётся из `.env.ml.local` либо вводится скрыто и сохраняется в этот игнорируемый файл. Не отправляйте его в Git/чат. Скрипт восстанавливает окружение после запуска. `ML_LIVE_MATERIAL_PATH` — путь только локального теста; production API его не принимает. Type choice_pair проверяет single_choice и multiple_choice; all — три типа. Артефакты — backend/build/ml-live, содержат учебный материал/ответы, не ключ.

## Приёмка

Реальное сохранение TXT с бесплатной моделью подтверждено на 0346e02; повторные сбои провайдера также зафиксированы. Один успешный сценарий не доказывает стабильность всех настроек. Нужны содержательная проверка, реальные интеграционные результаты, человеческое ревью, зелёный CI принятой версии и повторение другим участником. Локальные тесты не называются CI, ИИ-ревью не заменяет человеческий аппрув.
