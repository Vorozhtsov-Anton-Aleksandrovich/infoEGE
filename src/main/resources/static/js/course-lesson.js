// Страница урока: «Проверь себя» в теории, разбор по шагам и задачи без кода
// (ANSWER, CHOICE, TRUTH_TABLE, MATCH_GRAPH). Ответ сверяет сервер: POST .../answer.
(function () {
    'use strict';

    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    // Разбор пришёл с сервера (санитизированный HTML) — показываем его под задачей раскрытым.
    function showSolution(card, html) {
        const box = card.querySelector('.task-solution');
        if (!box || box.querySelector('details.solution')) {
            return;
        }
        const details = el('details', 'solution');
        details.appendChild(el('summary', null, 'Разбор решения'));
        const body = el('div', 'markdown');
        body.innerHTML = html;
        details.appendChild(body);
        box.replaceChildren(details);
        details.open = true;
    }

    window.CourseLesson = { showSolution: showSolution };

    // ---------- «Проверь себя»: варианты в теории (не оцениваются) ----------
    document.querySelectorAll('[data-quiz]').forEach(function (quiz) {
        const options = quiz.querySelectorAll('.quiz-option');
        const explain = quiz.querySelector('.quiz-explain');
        const verdict = el('div', 'quiz-verdict');
        verdict.setAttribute('aria-live', 'polite');
        quiz.querySelector('.quiz-options').after(verdict);
        options.forEach(function (option) {
            option.addEventListener('click', function () {
                if (quiz.classList.contains('quiz-done')) {
                    return;
                }
                if (option.dataset.correct === 'true') {
                    option.classList.add('right');
                    quiz.classList.add('quiz-done');
                    verdict.textContent = 'Верно!';
                    verdict.className = 'quiz-verdict right';
                    options.forEach(function (o) { o.disabled = true; });
                    if (explain) {
                        explain.open = true;
                    }
                } else {
                    option.classList.add('wrong');
                    option.disabled = true;
                    verdict.textContent = 'Не совсем. Подумай ещё и выбери другой вариант.';
                    verdict.className = 'quiz-verdict wrong';
                }
            });
        });
    });

    // ---------- Разбор по шагам: можно открывать шаги по одному ----------
    document.querySelectorAll('[data-steps]').forEach(function (box) {
        const items = Array.prototype.slice.call(box.querySelectorAll(':scope > ol > li, :scope > ul > li'));
        if (items.length < 2) {
            return;
        }
        const bar = el('div', 'steps-bar');
        const stepMode = el('button', 'steps-button', 'Пройти по шагам');
        const next = el('button', 'steps-button', 'Следующий шаг →');
        const all = el('button', 'steps-button link-button', 'Показать все шаги');
        const counter = el('span', 'hint');
        stepMode.type = next.type = all.type = 'button';
        next.hidden = all.hidden = true;
        bar.append(stepMode, next, all, counter);
        box.appendChild(bar);
        let shown = items.length;

        function update() {
            items.forEach(function (li, i) {
                li.hidden = i >= shown;
                li.classList.toggle('step-current', i === shown - 1 && shown < items.length);
            });
            const stepping = shown < items.length;
            next.hidden = !stepping;
            all.hidden = !stepping;
            stepMode.hidden = stepping;
            counter.textContent = stepping ? 'шаг ' + shown + ' из ' + items.length : '';
        }

        stepMode.addEventListener('click', function () { shown = 1; update(); next.focus(); });
        next.addEventListener('click', function () { shown = Math.min(items.length, shown + 1); update(); });
        all.addEventListener('click', function () { shown = items.length; update(); });
        update();
    });

    // ---------- Задачи без кода ----------
    function setupAnswerTask(card) {
        const type = card.dataset.type;
        const workspace = card.querySelector('.answer-workspace');
        const button = card.querySelector('.answer-check');
        const state = card.querySelector('.answer-state');
        const feedback = card.querySelector('.answer-feedback');
        const statusBadge = card.querySelector('.task-status');
        const url = '/api/courses/' + encodeURIComponent(card.dataset.course) + '/tasks/'
            + encodeURIComponent(card.dataset.taskId) + '/answer';
        const last = workspace.dataset.last || '';
        let busy = false;

        // Таблица истинности: клик по клетке «?» → 0 → 1 → 0.
        const cells = Array.prototype.slice.call(card.querySelectorAll('.tt-cell'));
        function setCell(cell, value) {
            cell.textContent = value;
            cell.classList.toggle('tt-zero', value === '0');
            cell.classList.toggle('tt-one', value === '1');
        }
        cells.forEach(function (cell) {
            cell.addEventListener('click', function () {
                setCell(cell, cell.textContent === '0' ? '1' : '0');
                clearMarks();
            });
        });
        const selects = Array.prototype.slice.call(card.querySelectorAll('.mg-select'));
        selects.forEach(function (s) { s.addEventListener('change', clearMarks); });

        // Последний ответ ученика — обратно в поля.
        if (last) {
            if (type === 'CHOICE') {
                const radio = card.querySelector('input[type=radio][value="' + CSS.escape(last) + '"]');
                if (radio) {
                    radio.checked = true;
                }
            } else if (type === 'TRUTH_TABLE' && last.length === cells.length) {
                cells.forEach(function (cell, i) { setCell(cell, last.charAt(i)); });
            } else if (type === 'MATCH_GRAPH') {
                last.split(',').forEach(function (v, i) {
                    if (selects[i]) {
                        selects[i].value = v;
                    }
                });
            }
        }

        function clearMarks() {
            card.querySelectorAll('.mark-wrong, .mark-right').forEach(function (n) {
                n.classList.remove('mark-wrong', 'mark-right');
            });
        }

        function collect() {
            if (type === 'ANSWER') {
                const value = card.querySelector('.answer-input').value.trim();
                return value ? { value: value } : { error: 'Введи ответ.' };
            }
            if (type === 'CHOICE') {
                const checked = card.querySelector('input[type=radio]:checked');
                return checked ? { value: checked.value } : { error: 'Выбери вариант ответа.' };
            }
            if (type === 'TRUTH_TABLE') {
                const empty = cells.filter(function (c) { return c.textContent !== '0' && c.textContent !== '1'; });
                if (empty.length) {
                    empty.forEach(function (c) { c.classList.add('mark-wrong'); });
                    return { error: 'Заполни все клетки столбца F (осталось ' + empty.length + ').' };
                }
                return { value: cells.map(function (c) { return c.textContent; }).join('') };
            }
            const emptySelects = selects.filter(function (s) { return !s.value; });
            if (emptySelects.length) {
                emptySelects.forEach(function (s) { s.classList.add('mark-wrong'); });
                return { error: 'Выбери номер для каждой вершины.' };
            }
            const values = selects.map(function (s) { return s.value; });
            const duplicates = values.filter(function (v, i) { return values.indexOf(v) !== i; });
            if (duplicates.length) {
                return { error: 'Один и тот же номер выбран для разных вершин: ' + duplicates.join(', ') + '.' };
            }
            return { value: values.join(',') };
        }

        function setState(solved) {
            if (card.classList.contains('task-solved')) {
                return;
            }
            card.classList.remove('task-new', 'task-tried');
            card.classList.add(solved ? 'task-solved' : 'task-tried');
            statusBadge.textContent = solved ? 'решена' : 'не решена';
        }

        function show(text, className) {
            feedback.replaceChildren(el('div', className, text));
            feedback.hidden = false;
        }

        async function check() {
            if (busy) {
                return;
            }
            const answer = collect();
            if (answer.error) {
                show(answer.error, 'answer-msg warn');
                return;
            }
            busy = true;
            button.disabled = true;
            state.textContent = 'Проверяем…';
            try {
                const headers = { 'Content-Type': 'application/json' };
                if (csrfMeta && csrfHeaderMeta) {
                    headers[csrfHeaderMeta.content] = csrfMeta.content;
                }
                const res = await fetch(url, {
                    method: 'POST', credentials: 'same-origin', headers: headers,
                    body: JSON.stringify({ answer: answer.value })
                });
                const json = (res.headers.get('Content-Type') || '').includes('json') ? await res.json() : null;
                if (!res.ok) {
                    show((json && json.error) || 'Не удалось проверить ответ (код ' + res.status + ').', 'answer-msg warn');
                } else if (json.correct) {
                    show('Верно! Задача решена.', 'answer-msg right');
                    if (type === 'TRUTH_TABLE') {
                        cells.forEach(function (c) { c.classList.add('mark-right'); });
                    }
                    setState(true);
                } else {
                    let text = 'Неверно.';
                    if (json.mismatches > 0 && type === 'TRUTH_TABLE') {
                        text += ' Ошибок в столбце F: ' + json.mismatches + '. Пересчитай строки ещё раз.';
                    } else if (json.mismatches > 0 && type === 'MATCH_GRAPH') {
                        text += ' Неверно сопоставлено вершин: ' + json.mismatches + '.';
                    } else {
                        text += ' Попробуй ещё раз.';
                    }
                    if (json.solutionHtml) {
                        text += ' Разбор решения открыт ниже.';
                    } else if (json.failsToSolution > 0 && card.querySelector('.task-solution')) {
                        text += ' Разбор откроется через ' + json.failsToSolution + ' ' + attempts(json.failsToSolution) + '.';
                    }
                    show(text, 'answer-msg wrong');
                    setState(json.taskSolved);
                }
                if (json && json.solutionHtml) {
                    showSolution(card, json.solutionHtml);
                }
            } catch (e) {
                show('Нет связи с сервером. Проверь интернет и попробуй ещё раз.', 'answer-msg warn');
            } finally {
                busy = false;
                button.disabled = false;
                state.textContent = '';
            }
        }

        function attempts(n) {
            const m10 = n % 10;
            const m100 = n % 100;
            if (m10 === 1 && m100 !== 11) {
                return 'неудачную попытку';
            }
            return m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? 'неудачные попытки' : 'неудачных попыток';
        }

        button.addEventListener('click', check);
        const input = card.querySelector('.answer-input');
        if (input) {
            input.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    check();
                }
            });
        }
    }

    document.querySelectorAll('.course-task[data-type]:not([data-type="CODE"])').forEach(setupAnswerTask);

    // Ссылка вида #id-задачи раскрывает её.
    if (location.hash) {
        const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
        if (target && target.classList.contains('course-task')) {
            target.open = true;
        }
    }
})();
