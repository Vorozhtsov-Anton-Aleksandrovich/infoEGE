# ЕГЭ-тренажёр

Закрытая платформа репетитора по ЕГЭ по информатике. План: см. постановку задачи,
архитектура и схема БД: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Требования
- JDK 21
- Docker (для `docker compose` и для тестов на Testcontainers)

## Запуск всего в Docker

```bash
docker compose up --build
```

Приложение: http://localhost:8080, вход `teacher` / `teacher`
(переопределяется переменными `TEACHER_LOGIN`, `TEACHER_PASSWORD`, `TEACHER_NAME`).

Преподаватель создаётся только при первом запуске, если в БД ещё нет преподавателя.

## Запуск из IDE / Maven (профиль dev)

```bash
docker compose up -d db      # только база, на localhost:15432
./mvnw spring-boot:run       # или кнопка Run у InfoEgeApplication в IntelliJ
```

http://localhost:8080, вход `teacher` / `teacher` (значения по умолчанию профиля dev).
База проекта проброшена на порт 15432, чтобы не конфликтовать с локально установленными PostgreSQL (5432, 5433, ...).
Если запущен весь `docker compose up`, порт 8080 занят контейнером приложения: остановите его
(`docker compose stop app`) перед запуском из IDE.

## Тесты

```bash
./mvnw verify
```

## Переменные окружения

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` или `prod` | `dev` |
| `DB_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:15432/infoege` |
| `DB_USER`, `DB_PASSWORD` | доступ к БД | `infoege` / `infoege` |
| `TEACHER_LOGIN`, `TEACHER_PASSWORD` | аккаунт преподавателя при первом запуске | в dev `teacher`/`teacher`, в prod обязательны |
| `TEACHER_NAME` | отображаемое имя преподавателя | `Преподаватель` |
| `STORAGE_DIR` | каталог загруженных файлов | `./data/files` (в Docker `/app/data/files`) |
| `MAX_FILE_SIZE` | лимит размера одного файла | `20MB` |
| `APP_TIMEZONE` | часовой пояс для сроков в интерфейсе | `Europe/Moscow` |

Разрешённые расширения файлов задаются в `application.yml` (`app.storage.*-extensions`).

## Банк заданий
- Условие и разбор пишутся в Markdown (таблицы GFM, `<sub>`, `<sup>`), HTML санитизируется.
- Картинку в условие или разбор можно вставить через Ctrl+V или перетащить файл в поле (у сохранённого задания):
  она загружается в файлы задания и вставляется как `![](file:image.png)`. Вручную: загрузить в «Картинки для условия»
  и написать `![подпись](file:имя.png)`.
- Разбор необязателен: галочка «Добавить разбор (решение)»; без неё разбор не сохраняется.
- Правильный ответ: одна строка таблицы на строку текста, ячейки через пробел или `;`.

## Ученики и работы
- Преподаватель создаёт приглашение («Ученики» → имя → «Создать ссылку»). Ссылка показывается один раз,
  в БД хранится только SHA-256 токена, срок 7 дней, повторно не работает.
- По ссылке ученик задаёт логин и пароль. Для сброса пароля преподаватель выдаёт новую ссылку на странице ученика.
- Деактивация запрещает вход и сразу завершает открытые сессии ученика.
- Подборка: свой порядок заданий, лимит времени необязателен. Вариант: задания по номерам ЕГЭ, лимит обязателен.
- Подборку, которую уже назначили, удалить нельзя, но можно скопировать как шаблон.
  Задание из подборки при удалении переносится в архив.

## Тренажёр
- Ученик открывает работу и нажимает «Начать». Создаётся попытка, правильные ответы и баллы копируются в неё.
- Таймер, истечение и учёт времени считает сервер; клиентский таймер только показывает время.
- Ответ учитывается только после кнопки «Сохранить ответ» (или Enter в поле ответа), как на станции КЕГЭ.
  Изменённый, но не сохранённый ответ отмечен на кнопке задания красной чертой; при завершении такие изменения
  не отправляются, диалог «Завершить выполнение» предупреждает о них. При обрыве сети нажатое сохранение
  повторяется, пока сервер его не примет. Обновление страницы и повторный вход возвращают в ту же попытку.
- Файлы заданий ученик скачивает только через `/trainer/attempts/{id}/files/{fileId}` с проверкой доступа.

## Результаты
- Автопроверка при завершении или истечении времени. Стратегия задаётся в задании:
  «всё или ничего» либо частичный балл по строкам.
- «Результаты» у преподавателя: статус, балл, общее время по каждому назначению, фильтры по работе и ученику,
  экспорт в CSV (сводка и по заданиям; разделитель «;», UTF-8 с BOM для Excel).
- «Подробно»: ответы ученика, правильные ответы, баллы, время на каждое задание, число смен ответа,
  ручная корректировка итогового балла с комментарием.
- Страница ученика у преподавателя: сводка по сданным работам и по номерам ЕГЭ.
- Ученик видит результаты по настройке назначения: сразу, после срока или никогда (только преподаватель).

## Безопасность
- Вход: не больше 5 неудачных попыток на пару «логин + IP» и 30 на IP за 15 минут, затем вход
  с этого адреса блокируется на 15 минут. Счётчики в памяти, перезапуск приложения их сбрасывает.
- Cookie сессии: `HttpOnly`, `SameSite=Lax`, в prod `Secure`. CSRF на всех формах и запросах тренажёра.
- Заголовки: строгий CSP (только свои скрипты и стили), `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`,
  `X-Content-Type-Options: nosniff`, HSTS (Caddy).
- Ошибки 5xx пишутся в лог со стеком, адресом, пользователем и коротким номером; номер показывается на странице ошибки.
  Логи: `docker compose -f docker-compose.prod.yml logs app` (ротация json-file: 5 файлов по 20 МБ).

---

# Продакшен

Схема: VPS с Docker → Caddy (HTTPS, Let's Encrypt) → приложение → PostgreSQL.
Наружу открыты только порты 80 и 443. Образ собирает GitHub Actions и кладёт в GitHub Container Registry.

Файлы: `Dockerfile`, `docker-compose.prod.yml`, `Caddyfile`, `.env.example`, `scripts/backup.sh`, `scripts/restore.sh`,
`.github/workflows/deploy.yml`.

## 1. Сервер (Ubuntu 24.04, 2 ГБ RAM достаточно)

```bash
# под root
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp && ufw enable

mkdir -p /opt/infoege/scripts /var/backups/infoege
chown -R deploy:deploy /opt/infoege /var/backups/infoege
```

Вход по SSH только по ключу: в `/etc/ssh/sshd_config` `PasswordAuthentication no`, затем `systemctl restart ssh`.

## 2. Домен
A-запись (и AAAA, если есть IPv6) домена на IP сервера. Caddy получит сертификат сам при первом запуске,
когда домен уже указывает на сервер и порты 80/443 открыты.

## 3. Конфигурация на сервере (под пользователем deploy)

```bash
cd /opt/infoege
# скопируйте с компьютера: docker-compose.prod.yml, Caddyfile, .env.example, scripts/*.sh
#   scp docker-compose.prod.yml Caddyfile .env.example deploy@SERVER:/opt/infoege/
#   scp scripts/*.sh deploy@SERVER:/opt/infoege/scripts/
cp .env.example .env && chmod 600 .env
nano .env        # DOMAIN, ACME_EMAIL, IMAGE, пароли (openssl rand -base64 24)
chmod +x scripts/*.sh
```

Если пакет образа в GHCR приватный (по умолчанию), один раз войдите в реестр токеном
с правом `read:packages` (GitHub → Settings → Developer settings → Personal access tokens):

```bash
echo "<TOKEN>" | docker login ghcr.io -u <github-login> --password-stdin
```

## 4. Деплой через GitHub Actions
В репозитории: Settings → Secrets and variables → Actions.

| Тип | Имя | Значение |
|---|---|---|
| variable | `DEPLOY_HOST` | IP или имя сервера |
| variable | `DEPLOY_USER` | `deploy` |
| variable | `DEPLOY_PATH` | `/opt/infoege` |
| variable | `DEPLOY_DOMAIN` | домен сайта (для проверки health) |
| secret | `DEPLOY_SSH_KEY` | приватный ключ, публичная часть в `~deploy/.ssh/authorized_keys` |
| secret | `DEPLOY_KNOWN_HOSTS` | вывод `ssh-keyscan <сервер>` |

Ключ для деплоя: `ssh-keygen -t ed25519 -f deploy_key -N ""`.

После каждого зелёного CI на `main` workflow «Deploy»:
1. собирает образ и публикует `ghcr.io/<owner>/infoege:<sha>` и `:latest`;
2. копирует на сервер `docker-compose.prod.yml`, `Caddyfile`, скрипты;
3. делает бэкап (если БД уже запущена), прописывает версию в `.env`, `docker compose pull && up -d`;
4. ждёт `https://<домен>/actuator/health` = `UP`.

Пока `DEPLOY_HOST` не задан, workflow пропускается. Запустить вручную: Actions → Deploy → Run workflow.

Первый запуск вручную (если ещё не настроен Actions):

```bash
cd /opt/infoege
docker compose -f docker-compose.prod.yml --env-file .env up -d
docker compose -f docker-compose.prod.yml --env-file .env logs -f app   # «Teacher account ... created»
curl https://<домен>/actuator/health
```

Откат на предыдущую версию: `IMAGE_TAG=<старый sha>` в `.env` и `docker compose ... up -d`.
Миграции БД необратимы: если новая версия уже изменила схему, откатывайтесь восстановлением бэкапа,
сделанного перед деплоем.

## 5. Бэкапы

`scripts/backup.sh` делает `pg_dump` (формат custom) и архив файлов заданий в один
`infoege-ГГГГММДД-ЧЧММСС.tar` (права 600) с `.sha256`, удаляет архивы старше `BACKUP_KEEP_DAYS` (14 дней).

```bash
crontab -e   # под deploy
30 3 * * * /opt/infoege/scripts/backup.sh >> /var/backups/infoege/backup.log 2>&1
```

Бэкап на том же сервере не спасает от потери сервера: копируйте архивы наружу, например
`rclone copy /var/backups/infoege remote:infoege-backups` следующей строкой в cron или
снимками диска у хостера.

## 6. Восстановление и его проверка

```bash
cd /opt/infoege
scripts/restore.sh /var/backups/infoege/infoege-20261104-033000.tar
```

Скрипт проверяет контрольную сумму, просит подтверждения, останавливает приложение, пересоздаёт схему БД,
загружает дамп, заменяет файлы заданий и запускает приложение (Flyway докатит миграции,
если бэкап старше текущей версии).

Проверять восстановление стоит регулярно (раз в месяц) и не на боевом сервере:

1. На отдельной машине с Docker положите `docker-compose.prod.yml`, `Caddyfile`, `scripts/`
   и `.env` с `DOMAIN=localhost` и тем же `IMAGE`.
2. `docker compose -f docker-compose.prod.yml --env-file .env up -d`, дождитесь `https://localhost/actuator/health`
   (сертификат локальный: `curl -k`).
3. `scripts/restore.sh <архив> --yes`.
4. Войдите преподавателем: в банке те же задания, файлы скачиваются, в «Результатах» те же попытки.
   Быстрая сверка: `docker compose ... exec db psql -U infoege -c "select count(*) from task; select count(*) from attempt;"`
   на обеих машинах.
5. `docker compose ... down -v` на проверочной машине.

Такая проверка выполнена при разработке: задание с файлом → бэкап → `down -v` → чистый запуск → restore →
задание, правильный ответ и содержимое файла на месте.
