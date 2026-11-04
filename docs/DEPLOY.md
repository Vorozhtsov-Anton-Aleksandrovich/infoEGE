# Первый деплой на Ubuntu 24.04 (пошагово)

Итог: сайт `https://ваш-домен` с автоматическим сертификатом Let's Encrypt.
На сервере работают три контейнера:
- `caddy` — HTTPS, единственный, кто открыт наружу (порты 80 и 443);
- `app` — приложение, доступно только внутри Docker;
- `db` — PostgreSQL, доступна только внутри Docker.

Образ приложения собирается прямо на сервере, GitHub Actions для первого деплоя не нужен
(автодеплой можно настроить потом, см. раздел «Продакшен» в [README](../README.md)).

Команды с `$` выполняются у вас на компьютере, остальные — на сервере.

## 0. Что понадобится
- VPS с Ubuntu 24.04, от 2 ГБ RAM, публичный IP.
- Домен, у которого A-запись указывает на IP сервера (у регистратора домена: DNS → A → IP).
  Проверка: `$ nslookup ege.example.ru` показывает IP сервера.
- SSH-доступ к серверу под root (данные выдаёт хостер).

## 1. Подготовка сервера (под root)

```bash
ssh root@IP_СЕРВЕРА

apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh          # Docker и docker compose

# отдельный пользователь для приложения
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh && cp ~/.ssh/authorized_keys /home/deploy/.ssh/ 2>/dev/null
chown -R deploy:deploy /home/deploy/.ssh

# файрвол: SSH, HTTP, HTTPS
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw enable

# каталоги приложения и бэкапов
mkdir -p /opt/infoege /var/backups/infoege
chown -R deploy:deploy /opt/infoege /var/backups/infoege
```

Если RAM всего 2 ГБ, добавьте swap, чтобы сборка образа не упала по памяти:

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 2. Код на сервер

Вариант А — репозиторий на GitHub:

```bash
su - deploy
git clone https://github.com/<логин>/<репозиторий>.git /opt/infoege
```

Вариант Б — без GitHub, архивом с вашего компьютера (из папки проекта):

```bash
$ git archive --format=tar.gz -o infoege.tar.gz HEAD
$ scp infoege.tar.gz deploy@IP_СЕРВЕРА:/opt/infoege/
```
```bash
su - deploy
cd /opt/infoege && tar xzf infoege.tar.gz && rm infoege.tar.gz
```

## 3. Настройки (.env)

```bash
cd /opt/infoege
cp .env.example .env && chmod 600 .env
openssl rand -base64 24        # пароль для БД
openssl rand -base64 18        # пароль преподавателя
nano .env
```

Заполните:
- `DOMAIN` — ваш домен без `https://`, например `ege.example.ru`;
- `ACME_EMAIL` — ваша почта (Let's Encrypt пишет о проблемах с сертификатом);
- `POSTGRES_PASSWORD` — первый сгенерированный пароль;
- `TEACHER_LOGIN` — ваш логин для входа, например `ivanova`;
- `TEACHER_PASSWORD` — второй пароль (не короче 12 символов, сохраните его в менеджере паролей).

Сохранение в nano: Ctrl+O, Enter, Ctrl+X. Файл `.env` никуда не копируйте и не коммитьте.

Проверка, что все обязательные значения заданы (выведет ошибку с именем пустой переменной):

```bash
docker compose -f docker-compose.prod.yml --env-file .env config --quiet && echo OK
```

## 4. Запуск

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Первая сборка идёт 3–10 минут. Затем:

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps          # все три контейнера Up, db (healthy)
docker compose -f docker-compose.prod.yml --env-file .env logs app | grep "Teacher account"
docker compose -f docker-compose.prod.yml --env-file .env logs caddy | grep -i certificate
curl https://ВАШ_ДОМЕН/actuator/health                                  # {"status":"UP"}
```

Откройте `https://ВАШ_ДОМЕН` и войдите логином и паролем из `.env`.

## 5. Ежедневные бэкапы

```bash
chmod +x scripts/*.sh
scripts/backup.sh              # проверка: в /var/backups/infoege появился .tar
crontab -e                     # добавьте строку:
30 3 * * * /opt/infoege/scripts/backup.sh >> /var/backups/infoege/backup.log 2>&1
```

Восстановление и проверка бэкапов описаны в README, разделы 5–6 «Продакшен».

## 6. Обновление до новой версии

```bash
scripts/backup.sh                          # миграции БД необратимы, бэкап обязателен
git pull                                   # или заново распакуйте архив (вариант Б)
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Данные при этом не теряются: БД и загруженные файлы лежат в Docker volume
`infoege_db-data` и `infoege_app-files`, сертификаты — в `infoege_caddy-data`.
**Никогда не запускайте `down -v`** на сервере: флаг `-v` удаляет эти volume вместе со всеми данными.

## Если что-то не так

| Симптом | Причина и решение |
|---|---|
| `required variable ... is missing a value` | В `.env` не заполнена названная переменная. |
| `app` перезапускается, в логах `TEACHER_PASSWORD is too weak` | Пароль короче 12 символов или равен логину. Исправьте `.env`, снова `up -d`. |
| Браузер ругается на сертификат, в логах caddy `challenge failed` | Домен ещё не указывает на сервер или закрыты порты 80/443. Проверьте `nslookup` и `ufw status`, затем `docker compose ... restart caddy`. |
| Сменили `TEACHER_PASSWORD` в `.env`, а вход по-старому | Преподаватель создаётся только один раз, при пустой БД. Пароль из `.env` после этого не читается. |
| Сборка падает с `Killed` | Не хватает памяти: добавьте swap (шаг 1). |

Логи: `docker compose -f docker-compose.prod.yml --env-file .env logs -f app` (выход — Ctrl+C).
