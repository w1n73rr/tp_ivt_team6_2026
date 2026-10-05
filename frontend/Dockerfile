# ---------- Stage 1: сборка Angular ----------

# Берём образ с Node.js 24.
# Alpine - облегчённый Linux-образ.
FROM node:24-alpine AS build

# Все следующие команды выполняются внутри /app.
WORKDIR /app

# Сначала копируем только файлы с зависимостями.
COPY package.json package-lock.json ./

# Устанавливаем зависимости строго по package-lock.json.
RUN npm ci 
#храним кэш ещё и не делаем повторных ненужных действий, если поменялось что-то другое

# Теперь копируем остальные файлы frontend-проекта из текущей папки в текущий workdir.
COPY . .

# Собираем production-версию Angular.
RUN npm run build


# ---------- Stage 2: запуск приложения ----------

# Берём лёгкий Nginx.
FROM nginx:stable-alpine

# Удаляем стандартную страницу Nginx.
RUN rm -rf /usr/share/nginx/html/*

COPY nginx.conf /etc/nginx/conf.d/default.conf

# Копируем результат Angular build из первой стадии.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Nginx внутри контейнера слушает порт 80.
EXPOSE 80

# Запускаем Nginx и оставляем его работать на foreground.
CMD ["nginx", "-g", "daemon off;"]
