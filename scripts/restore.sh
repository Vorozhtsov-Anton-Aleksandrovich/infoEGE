#!/usr/bin/env bash
# Восстановление из архива scripts/backup.sh. ЗАМЕНЯЕТ текущие БД и файлы содержимым архива.
# Использование: scripts/restore.sh /var/backups/infoege/infoege-20261104-033000.tar [--yes]
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup.tar> [--yes]" >&2
    exit 1
fi
archive="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
confirmed="${2:-}"

cd "$(dirname "$0")/.."
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

env_value() {
    local value
    value="$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- | tr -d '\r')" || true
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    printf '%s' "$value"
}

POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

[ -f "$archive" ] || { echo "Archive not found: $archive" >&2; exit 1; }
if [ -f "$archive.sha256" ]; then
    (cd "$(dirname "$archive")" && sha256sum -c "$(basename "$archive").sha256")
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
(cd "$(dirname "$archive")" && tar -xf "$(basename "$archive")" -C "$work")
[ -f "$work/db.dump" ] && [ -f "$work/files.tar.gz" ] || { echo "Archive is incomplete" >&2; exit 1; }

if [ "$confirmed" != "--yes" ]; then
    read -r -p "Текущие данные будут заменены данными из $(basename "$archive"). Продолжить? [y/N] " answer
    [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "Cancelled"; exit 1; }
fi

echo "[$(date -Is)] restore started from $archive"

# 1. Останавливаем приложение, чтобы никто не писал в БД и файлы.
compose stop app

# 2. БД: пересоздаём схему и загружаем дамп.
compose up -d db
until compose exec -T db pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do sleep 1; done
compose exec -T db psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error < "$work/db.dump"

# 3. Файлы: очищаем volume и распаковываем архив.
compose run --rm --no-deps -T --entrypoint sh app \
    -c 'rm -rf /app/data/files && tar -xzf - -C /app/data && mkdir -p /app/data/files' < "$work/files.tar.gz"

# 4. Запускаем приложение. Flyway применит миграции, если бэкап сделан на более старой версии.
compose up -d app

echo "[$(date -Is)] restore finished. Check: open the site, log in, look at tasks, files and results."
