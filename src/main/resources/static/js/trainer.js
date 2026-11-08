// Тренажёр. Время и истечение попытки считает сервер; здесь таймер только показывает время
// и подстраивается под remainingSeconds из каждого ответа сервера.
// Ответ уходит на сервер только по кнопке «Сохранить ответ» (или Enter); несохранённые изменения не учитываются.
// В режиме «Проверить ответ» (data-mode="instant") та же кнопка проверяет ответ: сервер сохраняет его
// и отвечает «верно/неверно»; исправлять можно сколько угодно, в зачёт идёт последний проверенный.
(function () {
    'use strict';

    const HEARTBEAT_MS = 15000;
    const MAX_RETRY_MS = 30000;

    const body = document.body;
    const api = body.dataset.api;
    const instant = body.dataset.mode === 'instant';
    const BUTTON_LABEL = instant ? '«Проверить ответ»' : '«Сохранить ответ»';
    const STATUS_CLASSES = ['status-not-opened', 'status-opened', 'status-saved',
        'status-correct', 'status-partial', 'status-wrong'];
    const VERDICT_TEXT = { correct: 'Верно', partial: 'Частично верно', wrong: 'Неверно' };
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    const sections = Array.from(document.querySelectorAll('.trainer-task'));
    const buttons = Array.from(document.querySelectorAll('.task-btn'));
    const timerEl = document.getElementById('timer');
    const statusEl = document.getElementById('save-status');
    const positionEl = document.getElementById('position');
    const prevBtn = document.getElementById('prev-btn');
    const nextBtn = document.getElementById('next-btn');
    const finishBottomBtn = document.getElementById('finish-bottom-btn');

    let current = -1;
    let closed = false;
    let authLost = false;

    // ---------- Запросы ----------

    class ClosedError extends Error {}
    class HttpError extends Error {
        constructor(status) { super('HTTP ' + status); this.status = status; }
    }

    async function post(path, data) {
        const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
        headers[csrfHeader] = csrfToken;
        const res = await fetch(api + path, {
            method: 'POST', headers: headers, body: JSON.stringify(data || {}),
            credentials: 'same-origin', redirect: 'manual'
        });
        if (res.status === 409) {
            const json = await res.json();
            onClosed(json.status);
            throw new ClosedError();
        }
        // Редирект на страницу входа или 403: сессия закончилась.
        if (res.type === 'opaqueredirect' || res.status === 401 || res.status === 403) {
            throw new HttpError(401);
        }
        if (!res.ok) {
            throw new HttpError(res.status);
        }
        return res.json();
    }

    function setStatus(state, text) {
        statusEl.dataset.state = state;
        statusEl.textContent = text;
    }

    // ---------- Таймер ----------

    let endsAt = body.dataset.remaining === undefined ? null : Date.now() + Number(body.dataset.remaining) * 1000;

    function syncRemaining(seconds) {
        if (seconds !== null && seconds !== undefined) {
            endsAt = Date.now() + seconds * 1000;
        }
    }

    function formatTime(total) {
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const s = total % 60;
        return [h, m, s].map(function (x) { return String(x).padStart(2, '0'); }).join(':');
    }

    function renderTimer() {
        if (endsAt === null || closed) {
            return;
        }
        const left = Math.max(0, Math.round((endsAt - Date.now()) / 1000));
        timerEl.textContent = formatTime(left);
        timerEl.classList.toggle('warning', left <= 300);
        if (left === 0) {
            onTimeUp();
        }
    }

    let timeUpHandled = false;

    function onTimeUp() {
        if (timeUpHandled) {
            return;
        }
        timeUpHandled = true;
        // Несохранённые изменения не отправляются. Через запас сервера (Attempt.GRACE) спрашиваем сервер:
        // он закрывает попытку, и экран закрывается по его ответу (409).
        setTimeout(async function () {
            await heartbeat(current);
            onClosed('EXPIRED');
        }, 6000);
    }

    // ---------- Навигация ----------

    function indexFromHash() {
        const n = parseInt(location.hash.replace('#', ''), 10);
        return n >= 1 && n <= sections.length ? n - 1 : 0;
    }

    function show(index) {
        if (index === current || index < 0 || index >= sections.length) {
            return;
        }
        if (current >= 0) {
            heartbeat(current);
        }
        current = index;
        sections.forEach(function (s, i) { s.hidden = i !== index; });
        buttons.forEach(function (b, i) { b.classList.toggle('current', i === index); });
        const btn = buttons[index];
        if (btn.classList.contains('status-not-opened')) {
            btn.classList.replace('status-not-opened', 'status-opened');
        }
        const last = index === sections.length - 1;
        prevBtn.disabled = index === 0;
        nextBtn.hidden = last;
        finishBottomBtn.hidden = !last;
        positionEl.textContent = (index + 1) + ' из ' + sections.length;
        history.replaceState(null, '', '#' + (index + 1));
        window.scrollTo(0, 0);
        heartbeat(index);
        const firstInput = sections[index].querySelector('.answer-cell');
        if (firstInput && !closed) {
            firstInput.focus({ preventScroll: true });
        }
    }

    buttons.forEach(function (b) {
        b.addEventListener('click', function () { show(Number(b.dataset.index)); });
    });
    prevBtn.addEventListener('click', function () { show(current - 1); });
    nextBtn.addEventListener('click', function () { show(current + 1); });

    // ---------- Сохранение ответа ----------

    // Последний сохранённый на сервере ответ каждого задания (для сравнения с тем, что сейчас в полях).
    const savedValue = sections.map(function (s, i) { return valueKey(collect(i)); });
    const saving = sections.map(function () { return false; });
    // Вердикт последней проверки (режим «Проверить ответ»): correct, partial, wrong или null.
    const verdicts = sections.map(function (s) {
        const state = s.querySelector('.answer-state').dataset.state;
        return VERDICT_TEXT[state] ? state : null;
    });

    function isAnswered(btn) {
        return ['status-saved', 'status-correct', 'status-partial', 'status-wrong']
            .some(function (c) { return btn.classList.contains(c); });
    }

    function taskId(index) {
        return Number(sections[index].dataset.taskId);
    }

    function collect(index) {
        const cells = [];
        sections[index].querySelectorAll('.answer-cell').forEach(function (input) {
            const r = Number(input.dataset.row);
            const c = Number(input.dataset.col);
            cells[r] = cells[r] || [];
            cells[r][c] = input.value;
        });
        return cells;
    }

    // Сервер обрезает пробелы по краям, поэтому и сравниваем без них.
    function valueKey(cells) {
        return JSON.stringify(cells.map(function (row) { return row.map(function (v) { return v.trim(); }); }));
    }

    function isEmpty(cells) {
        return cells.every(function (row) { return row.every(function (v) { return v.trim() === ''; }); });
    }

    function isDirty(index) {
        return valueKey(collect(index)) !== savedValue[index];
    }

    function setAnswerState(index, state, text) {
        const el = sections[index].querySelector('.answer-state');
        el.dataset.state = state;
        el.textContent = text;
    }

    function refreshDirty(index) {
        const dirty = isDirty(index);
        buttons[index].classList.toggle('dirty', dirty);
        if (dirty) {
            setAnswerState(index, 'dirty', instant ? 'Ответ изменён, но не проверен' : 'Ответ изменён, но не сохранён');
        } else if (instant) {
            const v = verdicts[index];
            setAnswerState(index, v || 'empty', v ? VERDICT_TEXT[v] : 'Ответ не проверен');
        } else {
            const saved = buttons[index].classList.contains('status-saved');
            setAnswerState(index, saved ? 'saved' : 'empty', saved ? 'Ответ сохранён' : 'Ответ не сохранён');
        }
    }

    async function save(index, retryMs) {
        if (closed || authLost || saving[index]) {
            return;
        }
        const cells = collect(index);
        saving[index] = true;
        setAnswerState(index, 'saving', instant ? 'Проверка…' : 'Сохранение…');
        try {
            const result = await post(instant ? '/check' : '/answers', { taskId: taskId(index), cells: cells });
            syncRemaining(result.remainingSeconds);
            savedValue[index] = valueKey(cells);
            const btn = buttons[index];
            btn.classList.remove.apply(btn.classList, STATUS_CLASSES);
            saving[index] = false;
            if (instant) {
                verdicts[index] = result.verdict || null;
                btn.classList.add(result.verdict ? 'status-' + result.verdict : 'status-opened');
                sections[index].querySelector('.check-count').textContent = 'Проверок: ' + result.checks;
                refreshDirty(index);
                if (result.verdict === 'partial') {
                    setAnswerState(index, 'partial', 'Частично верно: ' + result.points + ' из ' + result.maxPoints);
                }
                setStatus('saved', result.verdict
                    ? 'Задание ' + (index + 1) + ': ' + VERDICT_TEXT[result.verdict].toLowerCase()
                    : 'Ответ на задание ' + (index + 1) + ' стёрт');
                return;
            }
            btn.classList.add(isEmpty(cells) ? 'status-opened' : 'status-saved');
            refreshDirty(index);
            setStatus('saved', isEmpty(cells) ? 'Ответ на задание ' + (index + 1) + ' стёрт'
                : 'Ответ на задание ' + (index + 1) + ' сохранён');
        } catch (e) {
            saving[index] = false;
            if (e instanceof ClosedError) {
                return;
            }
            if (e instanceof HttpError && e.status === 401) {
                authLost = true;
                setAnswerState(index, 'error', 'Не сохранено');
                setStatus('error', 'Сеанс завершён. Войдите снова, чтобы сохранять ответы.');
                return;
            }
            if (e instanceof HttpError && e.status === 400) {
                setAnswerState(index, 'error', 'Ответ не принят сервером');
                return;
            }
            // Нет связи: повторяем, пока сервер не примет ответ, который ученик сохранил.
            const delay = retryMs ? Math.min(retryMs * 2, MAX_RETRY_MS) : 1000;
            setAnswerState(index, 'error', 'Нет связи. Повтор через ' + Math.round(delay / 1000) + ' с');
            setStatus('offline', 'Нет связи с сервером, ответ на задание ' + (index + 1) + ' ещё не сохранён');
            setTimeout(function () { save(index, delay); }, delay);
        }
    }

    sections.forEach(function (section, index) {
        section.querySelector('.save-answer-btn').addEventListener('click', function () { save(index); });
        section.querySelectorAll('.answer-cell').forEach(function (input) {
            input.addEventListener('input', function () { refreshDirty(index); });
            input.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    save(index);
                }
            });
        });
    });

    function dirtyNumbers() {
        return sections.map(function (s, i) { return i; }).filter(isDirty).map(function (i) { return i + 1; });
    }

    window.addEventListener('beforeunload', function (event) {
        if (!closed && (dirtyNumbers().length > 0 || saving.some(Boolean))) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    // ---------- Учёт времени (только при видимой вкладке) ----------

    let visibleSince = document.visibilityState === 'visible' ? Date.now() : null;
    let visibleMs = 0;

    function takeVisibleSeconds() {
        if (visibleSince !== null) {
            visibleMs += Date.now() - visibleSince;
            visibleSince = Date.now();
        }
        const seconds = Math.floor(visibleMs / 1000);
        visibleMs -= seconds * 1000;
        return seconds;
    }

    async function heartbeat(index) {
        if (closed || authLost || index < 0) {
            return;
        }
        const seconds = takeVisibleSeconds();
        try {
            const result = await post('/heartbeat', { taskId: taskId(index), seconds: seconds });
            syncRemaining(result.remainingSeconds);
        } catch (e) {
            if (!(e instanceof ClosedError)) {
                visibleMs += seconds * 1000; // засчитаем в следующий раз; сервер всё равно ограничит прирост
            }
        }
    }

    setInterval(function () {
        if (document.visibilityState === 'visible') {
            heartbeat(current);
        }
    }, HEARTBEAT_MS);

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            heartbeat(current);
            visibleSince = null;
        } else {
            visibleSince = Date.now();
        }
    });

    // ---------- Завершение ----------

    const finishDialog = document.getElementById('finish-dialog');

    async function openFinishDialog() {
        await waitForSaves(5000); // дождаться уже нажатых «Сохранить ответ» / «Проверить ответ»
        const unanswered = buttons
            .filter(function (b) { return !isAnswered(b); })
            .map(function (b) { return b.textContent.trim(); });
        document.getElementById('finish-unanswered').textContent = unanswered.length
            ? (instant ? 'Без проверенного ответа: ' : 'Без сохранённого ответа: ') + unanswered.join(', ') + '.'
            : 'Ответы сохранены на все задания.';
        const dirty = dirtyNumbers();
        const unsavedEl = document.getElementById('finish-unsaved');
        unsavedEl.hidden = dirty.length === 0;
        unsavedEl.textContent = (dirty.length === 1 ? 'Изменения в задании ' : 'Изменения в заданиях ') + dirty.join(', ')
            + (instant ? ' не проверены' : ' не сохранены') + ' и не будут учтены. Чтобы их учесть, вернитесь и нажмите '
            + BUTTON_LABEL + '.';
        finishDialog.showModal();
    }

    document.getElementById('finish-btn').addEventListener('click', openFinishDialog);
    finishBottomBtn.addEventListener('click', openFinishDialog);
    document.getElementById('finish-cancel').addEventListener('click', function () { finishDialog.close(); });

    document.getElementById('finish-confirm').addEventListener('click', async function () {
        this.disabled = true;
        await heartbeat(current);
        try {
            const result = await post('/finish');
            closed = true;
            location.href = result.redirect;
        } catch (e) {
            this.disabled = false;
            if (!(e instanceof ClosedError)) {
                finishDialog.close();
                setStatus('offline', 'Не удалось завершить: нет связи с сервером. Попробуйте ещё раз.');
            }
        }
    });

    // ---------- Пауза («Закончить потом») ----------

    const pauseDialog = document.getElementById('pause-dialog');
    if (pauseDialog) {
        document.getElementById('pause-btn').addEventListener('click', async function () {
            await waitForSaves(5000);
            const dirty = dirtyNumbers();
            const unsavedEl = document.getElementById('pause-unsaved');
            unsavedEl.hidden = dirty.length === 0;
            unsavedEl.textContent = (dirty.length === 1 ? 'Изменения в задании ' : 'Изменения в заданиях ') + dirty.join(', ')
                + (instant ? ' не проверены' : ' не сохранены') + ' и пропадут. Чтобы их учесть, нажмите ' + BUTTON_LABEL + '.';
            pauseDialog.showModal();
        });
        document.getElementById('pause-cancel').addEventListener('click', function () { pauseDialog.close(); });
        document.getElementById('pause-confirm').addEventListener('click', async function () {
            this.disabled = true;
            await heartbeat(current);
            try {
                const result = await post('/pause');
                closed = true; // страница уходит: не предупреждать о несохранённом
                location.href = result.redirect;
            } catch (e) {
                this.disabled = false;
                if (!(e instanceof ClosedError)) {
                    pauseDialog.close();
                    setStatus('offline', 'Не удалось поставить на паузу: нет связи с сервером. Попробуйте ещё раз.');
                }
            }
        });
    }

    function waitForSaves(timeoutMs) {
        const started = Date.now();
        return new Promise(function (resolve) {
            (function check() {
                if (!saving.some(Boolean) || Date.now() - started > timeoutMs) {
                    resolve();
                } else {
                    setTimeout(check, 100);
                }
            })();
        });
    }

    function onClosed(status) {
        if (closed) {
            return;
        }
        closed = true;
        document.querySelectorAll('.answer-cell, .save-answer-btn').forEach(function (el) { el.disabled = true; });
        if (finishDialog.open) {
            finishDialog.close();
        }
        if (pauseDialog && pauseDialog.open) {
            pauseDialog.close();
        }
        if (status === 'PAUSED') {
            document.getElementById('closed-title').textContent = 'Работа на паузе';
            document.getElementById('closed-text').textContent =
                'Работа поставлена на паузу в другой вкладке. Чтобы продолжить, откройте её снова со страницы работы.';
        } else if (status === 'FINISHED') {
            document.getElementById('closed-title').textContent = 'Работа завершена';
        } else if (endsAt !== null) {
            timerEl.textContent = '00:00:00';
        }
        document.getElementById('closed-dialog').showModal();
    }

    // ---------- Старт ----------

    if (sections.length > 0) {
        show(indexFromHash());
    }
    renderTimer();
    setInterval(renderTimer, 1000);
})();
