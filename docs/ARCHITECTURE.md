# Архитектура

## Структура пакетов

Пакеты по предметной области, внутри каждого: сущность, репозиторий, сервис, контроллер.

```
com.github.anton.infoege
├── InfoEgeApplication
├── config        SecurityConfig, TeacherBootstrap, свойства приложения    (этап 1)
├── web           HomeController, LoginController, обработка ошибок        (этап 1)
├── user          User, Role, UserRepository, AppUserDetailsService        (этап 1)
├── markdown      MarkdownRenderer (commonmark + санитизация HTML)         (этап 2)
├── storage       FileStorage, LocalFileStorage, FileValidator             (этап 2)
├── task          Task, TaskFile, Scoring, банк заданий для преподавателя  (этап 2)
├── taskset       TaskSet, TaskSetItem, подборки и варианты                (этап 3)
├── invite        Invite, одноразовые ссылки, регистрация                  (этап 3)
├── student       управление учениками со стороны преподавателя            (этап 3)
├── assignment    Assignment, назначение работ                             (этап 3)
├── attempt       Attempt, AttemptAnswer, тренажёр, heartbeat, планировщик (этап 4)
├── checking      AnswerNormalizer, AnswerChecker (стратегии проверки)     (этапы 4-5)
└── result        результаты ученика, панель преподавателя, CSV           (этап 5)
```

Шаблоны Thymeleaf: `templates/teacher/**`, `templates/student/**`, `templates/trainer/**`
(полноэкранный режим без меню), `templates/fragments/**`.

## Схема БД

Миграции Flyway (`db/migration`) добавляются поэтапно, каждая только с таблицами своего этапа.

```
users
  id              bigserial PK
  role            varchar(16)  NOT NULL  CHECK in (TEACHER, STUDENT)
  display_name    varchar(200) NOT NULL
  login           varchar(64)  NOT NULL UNIQUE
  password_hash   varchar(100) NOT NULL
  active          boolean      NOT NULL DEFAULT true
  created_at      timestamptz  NOT NULL

invite
  id                    bigserial PK
  token_hash            varchar(64) NOT NULL UNIQUE        -- SHA-256 hex, сам токен не хранится
  student_display_name  varchar(200) NOT NULL
  user_id               bigint NULL FK users               -- заполнено для перевыпуска (сброс пароля)
  expires_at            timestamptz NOT NULL
  used_at               timestamptz NULL
  created_by            bigint NOT NULL FK users
  created_at            timestamptz NOT NULL

task
  id              bigserial PK
  ege_number      int          NOT NULL                     -- 1..27, но в коде не зашивается
  title           varchar(300) NOT NULL
  statement_md    text         NOT NULL
  difficulty      int          NOT NULL CHECK 1..5
  max_points      int          NOT NULL CHECK >= 0
  scoring         varchar(16)  NOT NULL                     -- ALL_OR_NOTHING | PER_ROW
  answer_rows     int          NOT NULL CHECK >= 1
  answer_cols     int          NOT NULL CHECK >= 1
  correct_answer  jsonb        NOT NULL                     -- [["1","2"],["3","4"]]
  solution_md     text         NULL
  topic           varchar(200) NULL
  archived        boolean      NOT NULL DEFAULT false
  created_at      timestamptz  NOT NULL

task_file
  id              bigserial PK
  task_id         bigint NOT NULL FK task
  original_name   varchar(255) NOT NULL
  storage_key     varchar(100) NOT NULL UNIQUE              -- случайное имя на диске
  size_bytes      bigint NOT NULL
  content_type    varchar(100) NOT NULL
  kind            varchar(16)  NOT NULL                     -- ATTACHMENT | IMAGE (картинка в условии)
  UNIQUE (task_id, original_name)                           -- картинки ссылаются на файл по имени

task_set
  id                  bigserial PK
  type                varchar(16) NOT NULL                  -- COLLECTION | VARIANT
  title               varchar(300) NOT NULL
  description         text NULL
  time_limit_minutes  int NULL                              -- у VARIANT обязателен (CHECK)
  created_at          timestamptz NOT NULL

task_set_item
  task_set_id     bigint NOT NULL FK task_set ON DELETE CASCADE
  task_id         bigint NOT NULL FK task
  position        int    NOT NULL
  PK (task_set_id, task_id)

assignment
  id                  bigserial PK
  student_id          bigint NOT NULL FK users
  task_set_id         bigint NOT NULL FK task_set
  assigned_at         timestamptz NOT NULL
  deadline_at         timestamptz NULL
  show_results_mode   varchar(16) NOT NULL                  -- AFTER_FINISH | AFTER_DEADLINE | TEACHER_ONLY
                                                            -- AFTER_DEADLINE требует deadline_at (CHECK)

attempt
  id                  bigserial PK
  assignment_id       bigint NOT NULL FK assignment UNIQUE  -- одна попытка на назначение
  started_at          timestamptz NOT NULL
  expires_at          timestamptz NULL                      -- считается сервером из лимита времени
  finished_at         timestamptz NULL
  status              varchar(16) NOT NULL                  -- IN_PROGRESS | FINISHED | EXPIRED
  score               int NULL
  last_heartbeat_at   timestamptz NULL                      -- ограничение прироста времени на задание
  manual_score        int NULL                              -- этап 5: ручная корректировка
  manual_comment      text NULL                             -- этап 5
  manual_updated_at   timestamptz NULL                      -- этап 5

attempt_answer
  attempt_id          bigint NOT NULL FK attempt
  task_id             bigint NOT NULL FK task
  position            int    NOT NULL                       -- порядок задания в попытке
  answer              jsonb  NULL                           -- ответ ученика (таблица строк)
  answer_snapshot     jsonb  NOT NULL                       -- правильный ответ на момент старта
  points_snapshot     int    NOT NULL                       -- maxPoints на момент старта
  scoring_snapshot    varchar(16) NOT NULL                  -- стратегия проверки на момент старта
  is_correct          boolean NULL
  points              int NULL
  time_spent_seconds  int NOT NULL DEFAULT 0
  answer_changes      int NOT NULL DEFAULT 0
  first_opened_at     timestamptz NULL
  last_saved_at       timestamptz NULL
  PK (attempt_id, task_id)
```

### Отличия от модели в плане и почему

- `task.scoring`: в плане сказано «предусмотри стратегию проверки», это её поле.
- `task_file.kind`: отделяет картинки для условия от файлов для скачивания.
- `invite.user_id`: перевыпуск ссылки для сброса пароля должен знать, чей пароль сбрасывать.
- `attempt_answer.position` и `scoring_snapshot`: попытка не зависит от последующих правок подборки и задания.
- `attempt.manual_score`, `manual_comment`: ручная корректировка балла на этапе 5.
- Для будущей автооценки сложности в `attempt_answer` уже лежат `is_correct`, `points`,
  `time_spent_seconds`, `answer_changes`, `first_opened_at`, `last_saved_at`.

## Тренажёр (этап 4)

- Время попытки считает только сервер: `expires_at = min(started_at + лимит, срок назначения)`.
  Сохранения принимаются ещё 5 секунд после `expires_at` (`Attempt.GRACE`) из-за сетевой задержки.
- Попытку закрывают и запрос (ленивая проверка под блокировкой строки), и планировщик раз в 30 секунд;
  `finished_at` в обоих случаях равен `expires_at`.
- Все изменения попытки берут `SELECT ... FOR UPDATE` по попытке ученика: так сохранение, heartbeat
  и завершение не конфликтуют, а чужая попытка просто не находится (404).
- Время на задание: клиент раз в 15 секунд при видимой вкладке присылает, сколько секунд вкладка была видна.
  Сервер засчитывает не больше, чем прошло с прошлого heartbeat попытки, и не больше 20 секунд за запрос.

## Проверка и результаты (этап 5)

- Автопроверка выполняется при завершении и при истечении (и по запросу, и планировщиком).
  Сравнение по ячейкам после нормализации: trim, схлопывание пробелов, «,» = «.», без учёта регистра.
- ALL_OR_NOTHING: баллы только за полностью верную таблицу. PER_ROW: maxPoints × верные строки / все строки,
  округление вниз. Всё считается по снимкам из attempt_answer.
- Итоговый балл = manual_score, если преподаватель его выставил, иначе score.
- Результаты ученику: только своё назначение и только когда разрешает show_results_mode, иначе 404.

## Продакшен (этап 6)

```
Интернет ──443/80──► Caddy (TLS, HSTS, gzip) ──► app:8080 (Spring Boot, prod) ──► db:5432 (PostgreSQL 16)
                                                    │
                                                    └── volume app-files (файлы заданий)
```

- IP клиента для ограничения входа берётся из X-Forwarded-For через Tomcat RemoteIpValve
  (`server.forward-headers-strategy: native`); доверяются только внутренние адреса (сеть Docker).
- Тома: `db-data`, `app-files`, `caddy-data` (сертификаты), `caddy-config`.
- Бэкап: `pg_dump --format=custom` + tar тома файлов в один архив; восстановление пересоздаёт схему `public`.
