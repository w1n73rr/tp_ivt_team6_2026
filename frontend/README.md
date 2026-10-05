# Frontend

Frontend приложения на Angular.

## Локальный запуск

Установить зависимости:

```bash
npm ci
```

Запустить приложение:

```bash
npm start
```
После запуска приложение доступно по адресу:

```text
http://localhost:4200
```

## Запуск через Docker
Находясь в каталоге frontend, собрать Docker-образ:

```bash
docker build -t team6-frontend .
```

Запустить контейнер:

```bash
docker run --rm --name tp-team6-frontend -p 4200:80 team6-frontend
```
После запуска приложение доступно по адресу:

```text
http://localhost:4200
```