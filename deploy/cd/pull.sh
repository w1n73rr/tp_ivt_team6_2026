#!/usr/bin/env bash
set -euo pipefail
umask 077

cd /opt/tpivt
repo=w1n73rr/tp_ivt_team6_2026
api=https://api.github.com/repos/$repo
raw=https://raw.githubusercontent.com/$repo
state=/opt/tpivt/.cd
mkdir -p "$state"
exec 9>"$state/lock"
flock -n 9 || { echo 'Обновление уже выполняется'; exit 0; }

fail() { echo "$*" >&2; exit 1; }
get() { curl -fsSL --connect-timeout 10 --max-time 30 "$1"; }
valid_sha() { [[ $1 =~ ^[0-9a-f]{40}$ ]]; }

installed() {
    local service image sha
    for service in backend frontend; do
        image=$(docker inspect --format '{{.Config.Image}}' "tpivt-server-exam-ai-$service-1") || return 1
        sha=${image#ghcr.io/$repo-$service:sha-}
        valid_sha "$sha" || return 1
        echo "$sha"
    done
}

stage() {
    local sha=$1 dir="$state/$1" name
    [[ -f "$dir/server.yml" && -f "$dir/registry.yml" && -f "$dir/docker-compose.yml" ]] && return
    mkdir -p "$dir"
    get "$raw/$sha/docker-compose.yml" > "$dir/docker-compose.yml.tmp"
    for name in registry server; do
        if [[ $sha == 794982dd35f079f63671126de4638b76c040762a ]]; then
            get "$raw/$sha/compose.$name.yml" > "$dir/$name.yml.tmp"
        else
            get "$raw/$sha/deploy/compose/$name.yml" > "$dir/$name.yml.tmp"
        fi
    done
    for name in docker-compose registry server; do
        mv "$dir/$name.yml.tmp" "$dir/$name.yml"
    done
}

compose() {
    local sha=$1 dir="$state/$1"
    shift
    IMAGE_TAG="sha-$sha" docker compose --project-name tpivt-server --env-file /opt/tpivt/.env \
        -f "$dir/docker-compose.yml" -f "$dir/registry.yml" -f "$dir/server.yml" "$@"
}

migrations() {
    local sha=$1
    get "$api/contents/backend/src/main/resources/db/migration?ref=$sha" |
        python3 -c 'import json, sys
files = json.load(sys.stdin)
assert isinstance(files, list) and files and all(f["type"] == "file" for f in files)
for f in sorted(files, key=lambda f: f["name"]):
    print(f["name"], f["sha"])'
}

verify_http() {
    curl -fsS --connect-timeout 5 --max-time 15 http://127.0.0.1/ >/dev/null
}

mode=${1:-auto}
case "$mode" in
    auto|check) [[ $# -le 1 ]] || fail 'Использование: pull.sh [check | auto | apply SHA [--allow-migrations] | rollback]' ;;
    apply) [[ $# -eq 2 || ( $# -eq 3 && $3 == --allow-migrations ) ]] || fail 'Использование: pull.sh apply SHA [--allow-migrations]' ;;
    rollback) [[ $# -eq 1 ]] || fail 'Использование: pull.sh rollback' ;;
    *) fail 'Неизвестная команда' ;;
esac

[[ ! -f "$state/pending" ]] || fail 'Обнаружено незавершённое обновление. Сверьте контейнеры и состояние БД перед продолжением.'
versions=$(installed) || fail 'Не удалось определить SHA запущенных образов'
current=${versions%%$'\n'*}
[[ $versions == "$current"$'\n'"$current" ]] || fail 'Контейнеры приложения имеют разные ревизии'

run_id=
case "$mode" in
    auto|check)
        release=$(get "$api/actions/workflows/ci.yml/runs?branch=master&event=push&status=success&per_page=1" |
            python3 -c 'import json, sys
r = json.load(sys.stdin)["workflow_runs"][0]
assert (r["head_branch"], r["event"], r["conclusion"]) == ("master", "push", "success")
print(r["id"], r["head_sha"])')
        read -r run_id target <<< "$release"
        ;;
    apply) target=$2 ;;
    rollback)
        [[ -f "$state/previous" ]] || fail 'Предыдущая версия пока не записана'
        target=$(<"$state/previous")
        ;;
esac
valid_sha "$target" || fail 'Некорректный SHA релиза'
echo "Установлен $current, выбран $target"
[[ $current != "$target" ]] || exit 0
[[ $mode != check ]] || exit 0

if [[ $mode == auto && -f "$state/hold" && $(<"$state/hold") == "$target" ]]; then
    echo 'Эта ревизия оставлена для ручного решения'
    exit 0
fi

if [[ $mode == auto ]]; then
    [[ $run_id =~ ^[0-9]+$ ]] || fail 'Некорректный номер CI'
    get "$api/actions/runs/$run_id/jobs?per_page=100" |
        python3 -c 'import json, sys
jobs = json.load(sys.stdin)["jobs"]
assert any(j["name"] == "Публикация проверенных образов" and j["conclusion"] == "success" for j in jobs)'
    direction=$(get "$api/compare/$current...${target}?per_page=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
    [[ $direction == ahead ]] || fail 'Выбранный релиз не является продолжением установленного'
fi

current_migrations=$(migrations "$current")
target_migrations=$(migrations "$target")
same_schema=true
if [[ $current_migrations != "$target_migrations" ]]; then
    same_schema=false
    if [[ $mode == auto ]]; then
        printf '%s\n' "$target" > "$state/hold"
        echo 'Миграции изменились. Для этой версии требуется ручное обновление.'
        exit 0
    fi
    [[ $mode == apply && ${3:-} == --allow-migrations ]] || fail 'Миграции отличаются. Образный откат небезопасен.'
fi

stage "$current"
stage "$target"
compose "$target" config --quiet
compose "$target" pull exam-ai-backend exam-ai-frontend
printf '%s %s\n' "$current" "$target" > "$state/pending"

start_app() {
    compose "$1" up -d --no-build --pull never --no-deps --wait --wait-timeout 300 exam-ai-backend exam-ai-frontend &&
        verify_http
}

if start_app "$target"; then
    printf '%s\n' "$current" > "$state/previous"
    if [[ $mode == rollback ]]; then
        printf '%s\n' "$current" > "$state/hold"
    else
        rm -f "$state/hold"
    fi
    rm -f "$state/pending"
    echo "Запущен $target"
elif [[ $same_schema == true ]] && start_app "$current"; then
    printf '%s\n' "$target" > "$state/hold"
    rm -f "$state/pending"
    fail "Обновление не прошло проверку. Возвращён $current"
else
    fail 'Обновление не прошло проверку. Автоматическое обновление остановлено до проверки контейнеров и БД.'
fi
