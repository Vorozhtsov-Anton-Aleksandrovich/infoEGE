// Тренажёр. Время и истечение попытки считает сервер; здесь таймер только показывает время
// и подстраивается под remainingSeconds из каждого ответа сервера.
(function () {
    'use strict';

    const HEARTBEAT_MS = 15000;
    const SAVE_DEBOUNCE_MS = 700;
    const MAX_RETRY_MS = 30000;

    const body = document.body;
    const api = body.dataset.api;
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    const sections = Array.from(document.querySelectorAll('.trainer-task'));
    const buttons = Array.from(document.querySelectorAll('.task-btn'));
    const timerEl = document.getElementById('timer');
    const statusEl = document.getElementById('save-status');
    const positionEl = document.getElementById('position');
    const prevBtn = document.getElementById('prev-btn');
    const nextBtn = document.getElementById('next-btn');

    let current = -1;
    let closed = false;

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
        // Последняя попытка отправить несохранённое: сервер принимает ещё несколько секунд (Attempt.GRACE).
        // Затем запрос к серверу: он закрывает попытку, и экран закрывается по его ответу (409).
        flushSaves();
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
            saveNow(current);
        }
        current = index;
        sections.forEach(function (s, i) { s.hidden = i !== index; });
        buttons.forEach(function (b, i) { b.classList.toggle('current', i === index); });
        const btn = buttons[index];
        if (btn.classList.contains('status-not-opened')) {
            btn.classList.replace('status-not-opened', 'status-opened');
        }
        prevBtn.disabled = index === 0;
        nextBtn.disabled = index === sections.length - 1;
        positionEl.textContent = (index + 1) + ' из ' + sections.length;
        history.replaceState(null, '', '#' + (index + 1));
        window.scrollTo(0, 0);
        heartbeat(index);
    }

    buttons.forEach(function (b) {
        b.addEventListener('click', function () { show(Number(b.dataset.index)); });
    });
    prevBtn.addEventListener('click', function () { show(current - 1); });
    nextBtn.addEventListener('click', function () { show(current + 1); });

    // ---------- Автосохранение ----------

    const pending = new Map();   // taskId -> {index, cells}
    let saving = false;
    let retryMs = 0;
    let retryTimer = null;
    let debounceTimer = null;
    let authLost = false;

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

    function isEmpty(cells) {
        return cells.every(function (row) { return row.every(function (v) { return v.trim() === ''; }); });
    }

    function setStatus(state, text) {
        statusEl.dataset.state = state;
        statusEl.textContent = text;
    }

    function queue(index) {
        pending.set(taskId(index), { index: index, cells: collect(index) });
        setStatus('pending', 'Есть несохранённые изменения…');
    }

    function saveNow(index) {
        if (pending.has(taskId(index))) {
            clearTimeout(debounceTimer);
            flushSaves();
        }
    }

    async function flushSaves() {
        if (saving || closed || authLost) {
            return;
        }
        clearTimeout(retryTimer);
        const next = pending.entries().next();
        if (next.done) {
            setStatus('saved', 'Все ответы сохранены');
            return;
        }
        const id = next.value[0];
        const entry = next.value[1];
        pending.delete(id);
        saving = true;
        setStatus('saving', 'Сохранение…');
        try {
            const result = await post('/answers', { taskId: id, cells: entry.cells });
            syncRemaining(result.remainingSeconds);
            markSaved(entry.index, !isEmpty(entry.cells));
            retryMs = 0;
            saving = false;
            flushSaves();
        } catch (e) {
            saving = false;
            if (e instanceof ClosedError) {
                return;
            }
            if (!pending.has(id)) {
                pending.set(id, entry); // более новое изменение того же задания не затираем
            }
            if (e instanceof HttpError && e.status === 401) {
                authLost = true;
                setStatus('error', 'Сеанс завершён. Войдите снова, чтобы сохранить ответы.');
                return;
            }
            if (e instanceof HttpError && e.status === 400) {
                pending.delete(id);
                setStatus('error', 'Ответ не принят сервером (задание ' + (entry.index + 1) + ').');
                return;
            }
            retryMs = retryMs ? Math.min(retryMs * 2, MAX_RETRY_MS) : 1000;
            setStatus('offline', 'Нет связи с сервером. Повтор через ' + Math.round(retryMs / 1000) + ' с');
            retryTimer = setTimeout(flushSaves, retryMs);
        }
    }

    function markSaved(index, hasAnswer) {
        const btn = buttons[index];
        btn.classList.remove('status-not-opened', 'status-opened', 'status-saved');
        btn.classList.add(hasAnswer ? 'status-saved' : 'status-opened');
    }

    sections.forEach(function (section, index) {
        section.querySelectorAll('.answer-cell').forEach(function (input) {
            input.addEventListener('input', function () {
                queue(index);
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(flushSaves, SAVE_DEBOUNCE_MS);
            });
            input.addEventListener('blur', function () { saveNow(index); });
        });
    });

    window.addEventListener('online', function () { retryMs = 0; flushSaves(); });

    window.addEventListener('beforeunload', function (event) {
        if (!closed && (pending.size > 0 || saving)) {
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
            flushSaves();
        } else {
            visibleSince = Date.now();
        }
    });

    // ---------- Завершение ----------

    const finishDialog = document.getElementById('finish-dialog');

    document.getElementById('finish-btn').addEventListener('click', async function () {
        clearTimeout(debounceTimer);
        await flushSaves();
        await waitForSaves(5000);
        const unanswered = buttons
            .filter(function (b) { return !b.classList.contains('status-saved'); })
            .map(function (b) { return b.textContent.trim(); });
        document.getElementById('finish-unanswered').textContent = unanswered.length
            ? 'Без ответа: ' + unanswered.join(', ') + '.'
            : 'Ответы даны на все задания.';
        document.getElementById('finish-unsaved').hidden = pending.size === 0 && !saving;
        finishDialog.showModal();
    });

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

    function waitForSaves(timeoutMs) {
        const started = Date.now();
        return new Promise(function (resolve) {
            (function check() {
                if ((pending.size === 0 && !saving) || Date.now() - started > timeoutMs) {
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
        document.querySelectorAll('.answer-cell').forEach(function (input) { input.disabled = true; });
        if (finishDialog.open) {
            finishDialog.close();
        }
        if (status === 'FINISHED') {
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
