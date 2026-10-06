## tp_ivt_team6_2026
### Description
...
# Автотесты TY3 Backend

Проект автотестов REST API с использованием **Spring Boot Test** и **REST Assured**.

## Структура проекта автотестов
`src/test/java/tech/genna/TY3/`
- `config/` — конфигурация REST Assured и базовых спецификаций запросов/ответов.
- `client/` — HTTP-клиенты для взаимодействия с API эндпоинтами.
- `tests/base/` — базовые тестовые классы с логикой поднятия контекста Spring Boot.
- `tests/auth/` — автоматизированные тесты аутентификации и авторизации.

## Локальный запуск тестов

### Требования
- Java 25
- Gradle Wrapper (8.14)

### Команды запуска

1. Запуск всех автотестов из корня модуля `backend`:
   ```powershell
   .\gradlew test