# Автотест генерации — tpivt6_26-68

Код: `tests/ui/src/test/java/tech/genna/TY3/tests/ui/QuizGenerationUiTest.java`.
Page Object: GenerationPage, TestsPage, MaterialRow. Публичные XPath-элементы
с @Name; действия вида `generation.createButton.click()`. Chrome видимый.

## Окружения

- localhost:13001: настоящие backend/БД/S3/ML, тег live-ml.
- localhost:13002: настоящий backend/парсеры/БД/S3, управляемый ML на границе
  MlGenerator. Отдельная БД generation_ui_controlled. Control API — loopback:18084.

Тестовый сервер находится только в backend/src/test и не попадает в bootJar.
Защита разрешает ему только отдельную локальную generation_ui_controlled.
Ошибка извлечения внедряется перед парсером, ошибка сохранения — перед записью
в реальной транзакции. При ошибке сохранения транзакция откатывается; backend отдельно записывает
FAILED. Если БД недоступна, остаётся страховочный watchdog. Моки не проверяют качество настоящей модели.

Каждый тест регистрирует отдельного пользователя и загружает материал через UI.
Записи оставлены для анализа; повтор не требует ручного удаления файлов.

## Подготовка и запуск

Нужны Docker со стендом generation-acceptance, свежий образ frontend,
JDK 25 для backend, JDK 21+ для UI. Секреты — в игнорируемом
.env.generation.local. Этот файл не публикуется и не передаётся браузеру.

Первое окно PowerShell из C:\JavaPrj:

```powershell
.\deploy\scripts\start-generation-ui-test-frontend.ps1
.\deploy\scripts\start-generation-ui-test-server.ps1
```

Вторая команда работает до Ctrl+C; использует существующий PostgreSQL-контейнер
и отдельную БД без удаления данных. Поддерживает EnvFile/DatabaseContainer.
Provider key не загружается; переменные процесса очищаются после остановки.

В IDEA открыть QuizGenerationUiTest и нажать зелёный треугольник у метода.
Тег controlled автоматически выбирает :13002; live-ml использует адрес
из ui.local.properties (:13001 на текущем компьютере).

Все сценарии, включая настоящий ML:

```powershell
.\tests\ui\gradlew.bat -p .\tests\ui test --tests "*QuizGenerationUiTest"
```

Воспроизводимый набор без внешнего ML, с явным исключением live-ml:

```powershell
$env:GRADLE_OPTS='-Dui.excludeTags=live-ml'
.\tests\ui\gradlew.bat -p .\tests\ui test --tests "*QuizGenerationUiTest"
Remove-Item Env:GRADLE_OPTS
```

Один настоящий запуск: тот же Gradle с
`--tests "*QuizGenerationUiTest.generateAndPersistRealQuiz"`.
Он завершается ошибкой при RATE_LIMITED и не заменяется моками.

## Покрытие

TC-GEN-01–05: карточка, defaults сервера, 5/10, три сложности, один тип,
PDF/DOCX/TXT, передача параметров, polling, реальные сохранение вопросов,
UUID ответов, отсутствие дублей, replay и таблица после refresh.

TC-GEN-06 исключён пользователем: частичные 7 из 10 противоречат контракту.

TC-GEN-07–09: извлечение/ML/невалидный результат/нет вопросов/сохранение,
FAILED без готового теста, повтор, восстановление полей, блокировка настроек
в ожидании, уход на другую страницу без отмены задачи и перенаправления.

TC-GEN-10–12: некорректные параметры, JWT/владение, отсутствие вызовов ML и
новых задач при отклонении; HTTP 500 и настоящий browser offline.

HTML: tests/ui/build/reports/tests/test/index.html.
Безопасные JSON параметров, jobId и статусов: tests/ui/build/reports/generation.
JWT, provider key и env-файлы в них не записываются. Смысловая оценка
настоящих вопросов проводится отдельно по лекции, не по успешности моков.
Ранние API-отчёты не являются результатами текущего Selenide-прогона.
