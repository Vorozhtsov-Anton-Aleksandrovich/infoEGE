#!/usr/bin/env bash
# Бэкап БД (pg_dump) и загруженных файлов в один архив с ротацией старых копий.
# Запуск из любой папки: /opt/infoege/scripts/backup.sh
# Cron (каждый день в 03:30):
#   30 3 * * * /opt/infoege/scripts/backup.sh >> /var/log/infoege-backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

# Значение переменной из .env без source: значения с пробелами и кириллицей не ломают оболочку.
env_value() {
    local value
    value="$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- | tr -d '\r')" || true
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    printf '%s' "$value"
}

POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"
BACKUP_DIR="${BACKUP_DIR:-$(env_value BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/infoege}"
KEEP_DAYS="$(env_value BACKUP_KEEP_DAYS)"
KEEP_DAYS="${KEEP_DAYS:-14}"

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

timestamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "[$(date -Is)] backup started"

# 1. База: формат custom, чтобы pg_restore мог восстановить её целиком.
compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner > "$work/db.dump"

# 2. Файлы заданий из volume приложения. Временный контейнер работает и при остановленном приложении.
compose run --rm --no-deps -T --entrypoint tar app -C /app/data -czf - files > "$work/files.tar.gz"

# 3. Один архив и контрольная сумма.
# Имя архива относительное (cd в каталог бэкапов): tar не должен принять путь вида C:/... за host:path.
name="infoege-$timestamp.tar"
archive="$BACKUP_DIR/$name"
(cd "$BACKUP_DIR" && tar -cf "$name" -C "$work" db.dump files.tar.gz && chmod 600 "$name" && sha256sum "$name" > "$name.sha256")

echo "[$(date -Is)] backup written: $archive ($(du -h "$archive" | cut -f1))"

# 4. Ротация: удаляем архивы старше KEEP_DAYS дней.
find "$BACKUP_DIR" -maxdepth 1 -name 'infoege-*.tar*' -type f -mtime +"$KEEP_DAYS" -print -delete

echo "[$(date -Is)] backup finished"
