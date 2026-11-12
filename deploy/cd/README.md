# Pull CD на существующей ВМ

GitHub Actions публикует backend и frontend с тегом одного SHA после зелёного CI master.
Локальная служба проверяет CI, а Docker Compose применяет скачанные образы. Таймер
запускает один проход через две минуты после загрузки и через две минуты после
завершения предыдущего прохода. Журнал доступен через journalctl.

## Первый запуск

Из Git Bash в корне рабочего дерева отправить файлы на ВМ:

```bash
scp -i ~/.ssh/id_ed25519 -o IdentitiesOnly=yes \
  -o UserKnownHostsFile=/c/java-course/.ssh/known_hosts \
  deploy/cd/{pull.sh,tpivt-pull.service,tpivt-pull.timer} \
  deploy@84.54.56.192:~/
```

На ВМ установить локальную копию скрипта и файлов systemd:

```bash
sudo install -m 0755 ~/pull.sh /usr/local/sbin/tpivt-pull
sudo install -m 0644 ~/tpivt-pull.{service,timer} /etc/systemd/system/
sudo systemctl daemon-reload
sudo bash /usr/local/sbin/tpivt-pull check
```

В /opt/tpivt/.env сохранить действующие секреты и SERVER_ORIGIN. Для генерации
добавить серверный OPENROUTER_API_KEY и при необходимости указать OPENROUTER_MODEL.
Текущий релиз использует nvidia/nemotron-3.5-super-vl:free по умолчанию.
Скрипт задаёт IMAGE_TAG при вызове Compose и определяет установленную версию по
образам контейнеров. Конфигурации релизов лежат в /opt/tpivt/.cd/<SHA>.

## Копия перед первым обновлением

На ВМ выполнить блок целиком. На время копирования приложение будет недоступно.
Этот блок относится к текущему старому релизу 794982d с файлами Compose в корне.

```bash
(
  set -e
  umask 077
  cd /opt/tpivt
  backup="$HOME/tpivt-backup-$(date +%Y%m%d-%H%M%S)"
  mkdir -m 700 "$backup"
  cp .env docker-compose.yml compose.registry.yml compose.server.yml "$backup/"
  trap 'sudo docker compose --env-file .env -f docker-compose.yml -f compose.registry.yml -f compose.server.yml up -d --no-build --pull never --wait --wait-timeout 300' EXIT
  sudo docker stop tpivt-server-exam-ai-frontend-1 tpivt-server-exam-ai-backend-1
  sudo docker stop tpivt-server-seaweedfs-1
  sudo docker exec tpivt-server-exam-ai-db-1 sh -c 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup/postgres.dump"
  sudo docker exec -i tpivt-server-exam-ai-db-1 pg_restore -l < "$backup/postgres.dump" >/dev/null
  data_dir=$(sudo docker volume inspect --format '{{.Mountpoint}}' tpivt-server_seaweeddata)
  sudo tar -C "$data_dir" -czf - . > "$backup/seaweedfs.tar.gz"
  tar -tzf "$backup/seaweedfs.tar.gz" >/dev/null
  printf 'Копия: %s\n' "$backup"
)
```

Скачать postgres.dump и seaweedfs.tar.gz по SCP на компьютер. Защищённую копию .env
держать отдельно. Чтение структуры архивов подтверждает их базовую целостность.
Полное восстановление проверяется в отдельном окружении.

После копирования на ВМ применить уже проверенный SHA с новыми миграциями:

```bash
sudo bash /usr/local/sbin/tpivt-pull apply f22006d4b75fbac92ace6a98104eb01b8493fe6d --allow-migrations
sudo docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
curl -fsS http://127.0.0.1/ >/dev/null
```

Проверить вход, доступ к файлам и CRUD тестов. Затем повторить запуск через службу
и проверить журнал. Повторный проход с тем же SHA завершится без пересоздания.

```bash
sudo systemctl start tpivt-pull.service
sudo journalctl -u tpivt-pull.service -n 30 --no-pager
sudo systemctl enable --now tpivt-pull.timer
systemctl list-timers tpivt-pull.timer
```

## Откат

При ошибке запуска или HTTP-проверки скрипт возвращает старые образы, если список
и содержимое миграций совпадают. При таком же условии можно вручную выполнить
`sudo bash /usr/local/sbin/tpivt-pull rollback`. Снятая версия удерживается от
повторной автоматической установки. Автоматическое обновление релизов с новыми
миграциями отложено до ручного запуска. Если обновление с миграциями сорвалось,
файл pending блокирует следующую попытку до сверки контейнеров и восстановления
согласованного состояния БД. Для перехода с 794982d на f22006d возврат старых
образов может потребовать восстановления БД из копии.
