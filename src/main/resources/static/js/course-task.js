// Задачи курса на странице урока: редактор кода (CodeMirror 5), «Запустить» и «Проверить».
// Код выполняется в браузере (py-runner.js), выводы по тестам сверяет сервер (POST .../check).
(function () {
    'use strict';

    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    // Короткие объяснения частых ошибок для новичка — по типу исключения из последней строки traceback.
    const EXPLANATIONS = {
        EOFError: 'Программа ждала ввод через input(), но данные закончились. Впиши данные в поле «Ввод» — каждое значение на своей строке.',
        NameError: 'Python не знает такого имени. Проверь опечатки и большие/маленькие буквы. Текст нужно брать в кавычки.',
        SyntaxError: 'Ошибка в записи программы: не хватает скобки, кавычки или двоеточия. Посмотри на указанную строку и строку выше.',
        IndentationError: 'Неправильные отступы. Тело if, for, while и def сдвигается на 4 пробела, у всех строк одного блока отступ одинаковый.',
        TabError: 'В отступах смешаны пробелы и табуляция. Используй только пробелы.',
        ValueError: 'Неподходящее значение: например, int() получил не число или переменных слева меньше/больше, чем значений.',
        TypeError: 'Операция с несовместимыми типами, например число + строка. Не забыл int() вокруг input()?',
        ZeroDivisionError: 'Деление на ноль.',
        IndexError: 'Обращение к элементу за пределами строки или списка. Индексы идут от 0 до len − 1.',
        KeyError: 'В словаре нет такого ключа.',
        AttributeError: 'У значения нет такого метода. Проверь написание после точки.',
        FileNotFoundError: 'Файл не найден. Проверь имя файла из условия буква в букву.',
        RecursionError: 'Слишком глубокая рекурсия: нет базового случая или нужен sys.setrecursionlimit.'
    };

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

    function pre(text, className) {
        return el('pre', className || '', text === '' ? '(пусто)' : text);
    }

    function lastLine(traceback) {
        const lines = traceback.trim().split('\n');
        return lines[lines.length - 1];
    }

    // Блок ошибки: главная строка крупно, объяснение, полный traceback под спойлером.
    function errorBlock(traceback) {
        const box = el('div', 'py-error');
        const main = lastLine(traceback);
        box.appendChild(el('div', 'py-error-main', main));
        const type = (main.match(/^(\w+)/) || [])[1];
        if (EXPLANATIONS[type]) {
            box.appendChild(el('div', 'py-error-hint', EXPLANATIONS[type]));
        }
        const details = el('details');
        details.appendChild(el('summary', null, 'Полный текст ошибки'));
        details.appendChild(pre(traceback));
        box.appendChild(details);
        return box;
    }

    function timeoutBlock() {
        return el('div', 'py-error', 'Превышено время: программа работала дольше ' + (PyRunner.TIMEOUT_MS / 1000) +
            ' секунд и была остановлена. Возможно, цикл никогда не заканчивается или перебор слишком долгий.');
    }

    function draftKey(course, taskId) {
        return 'infoege:course-draft:' + course + ':' + taskId;
    }

    function readDraft(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (e) {
            return null;
        }
    }

    function writeDraft(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch (e) {
            // Хранилище недоступно (приватный режим) — черновик просто не сохраняется.
        }
    }

    function setupTask(card) {
        const course = card.dataset.course;
        const taskId = card.dataset.taskId;
        const apiBase = '/api/courses/' + encodeURIComponent(course) + '/tasks/' + encodeURIComponent(taskId);
        const textarea = card.querySelector('.code-input');
        const starter = card.querySelector('.code-starter').value;
        const inputField = card.querySelector('.run-input');
        const runButton = card.querySelector('.run-button');
        const checkButton = card.querySelector('.check-button');
        const resetButton = card.querySelector('.reset-button');
        const state = card.querySelector('.run-state');
        const output = card.querySelector('.run-output');
        const results = card.querySelector('.check-results');
        const statusBadge = card.querySelector('.task-status');

        // Цвет заголовка: task-new → task-tried после первой проверки → task-solved.
        function setState(value) {
            if (card.classList.contains('task-solved')) {
                return;
            }
            card.classList.remove('task-new', 'task-tried');
            card.classList.add('task-' + value);
            statusBadge.textContent = value === 'solved' ? 'решена' : 'не решена';
        }
        const key = draftKey(course, taskId);
        let tests = null;
        let busy = false;

        // Начальный код: последняя проверка с сервера → черновик в браузере → начальный код задачи.
        if (card.dataset.saved !== 'true') {
            const draft = readDraft(key);
            if (draft !== null) {
                textarea.value = draft;
            }
        }

        const editor = CodeMirror.fromTextArea(textarea, {
            mode: 'python',
            lineNumbers: true,
            indentUnit: 4,
            tabSize: 4,
            indentWithTabs: false,
            viewportMargin: Infinity,
            extraKeys: {
                Tab: function (cm) {
                    if (cm.somethingSelected()) {
                        cm.indentSelection('add');
                    } else {
                        cm.replaceSelection('    ', 'end');
                    }
                },
                'Shift-Tab': function (cm) { cm.indentSelection('subtract'); },
                'Ctrl-Enter': function () { run(); },
                'Cmd-Enter': function () { run(); }
            }
        });

        let draftTimer = null;
        editor.on('change', function () {
            clearTimeout(draftTimer);
            draftTimer = setTimeout(function () { writeDraft(key, editor.getValue()); }, 1000);
        });

        function setBusy(value, text) {
            busy = value;
            runButton.disabled = value;
            checkButton.disabled = value;
            state.textContent = text || '';
        }

        function showLoadingOnce() {
            if (!PyRunner.isLoaded()) {
                state.textContent = 'Загружаем Python… В первый раз это занимает несколько секунд, потом быстрее.';
            }
        }

        async function loadTests() {
            if (tests === null) {
                const res = await fetch(apiBase + '/tests', { credentials: 'same-origin' });
                if (!res.ok) {
                    throw new Error('Не удалось получить тесты (код ' + res.status + ').');
                }
                tests = await res.json();
            }
            return tests;
        }

        function showMessage(container, text, className) {
            container.replaceChildren(el('div', className || 'py-error', text));
            container.hidden = false;
        }

        async function run() {
            if (busy) {
                return;
            }
            setBusy(true, 'Выполняется…');
            showLoadingOnce();
            try {
                let files = {};
                if (card.dataset.hasFiles === 'true') {
                    const open = (await loadTests()).find(function (t) { return !t.hidden; });
                    files = open ? open.files : {};
                }
                const r = await PyRunner.run(editor.getValue(), inputField ? inputField.value : '', files);
                output.replaceChildren();
                output.appendChild(el('div', 'run-output-title', 'Вывод программы'));
                output.appendChild(pre(r.stdout));
                if (r.stderr) {
                    output.appendChild(el('div', 'run-output-title', 'stderr'));
                    output.appendChild(pre(r.stderr));
                }
                if (r.status === 'error') {
                    output.appendChild(errorBlock(r.error));
                } else if (r.status === 'timeout') {
                    output.appendChild(timeoutBlock());
                }
                output.hidden = false;
                setBusy(false, r.status === 'timeout' ? '' : 'Выполнено за ' + r.timeMs + ' мс');
            } catch (e) {
                showMessage(output, e.message);
                setBusy(false);
            }
        }

        function renderResults(response, runs) {
            results.replaceChildren();
            const summary = el('div', response.solved ? 'check-summary solved' : 'check-summary',
                response.solved ? 'Все тесты пройдены — задача решена!'
                    : 'Пройдено тестов: ' + response.passed + ' из ' + response.total);
            results.appendChild(summary);
            response.results.forEach(function (r) {
                const test = tests[r.index];
                const runResult = runs[r.index];
                const row = el('div', 'check-test ' + (r.passed ? 'passed' : 'failed'));
                const verdict = { passed: 'верно', wrong: 'неверный ответ', error: 'ошибка', timeout: 'превышено время' }[r.status];
                row.appendChild(el('div', 'check-test-title',
                    'Тест ' + (r.index + 1) + (r.hidden ? ' (скрытый)' : '') + ': ' + verdict));
                if (!r.hidden) {
                    // Открытый тест: ввод, ожидаемый и полученный вывод. У пройденного — свёрнуто.
                    const grid = el('div', 'check-io');
                    [['Ввод', test.input], ['Ожидалось', test.output], ['Получено', runResult.stdout]].forEach(function (pair) {
                        const cell = el('div');
                        cell.appendChild(el('div', 'hint', pair[0]));
                        cell.appendChild(pre(pair[1]));
                        grid.appendChild(cell);
                    });
                    if (r.passed) {
                        const details = el('details');
                        details.appendChild(el('summary', null, 'Показать ввод и вывод'));
                        details.appendChild(grid);
                        row.appendChild(details);
                    } else {
                        row.appendChild(grid);
                    }
                    if (r.status === 'error' && runResult.error) {
                        row.appendChild(errorBlock(runResult.error));
                    } else if (r.status === 'timeout') {
                        row.appendChild(timeoutBlock());
                    }
                }
                results.appendChild(row);
            });
            results.hidden = false;
            setState(response.taskSolved ? 'solved' : 'tried');
            if (response.solutionHtml && window.CourseLesson) {
                window.CourseLesson.showSolution(card, response.solutionHtml);
            }
        }

        async function check() {
            if (busy) {
                return;
            }
            setBusy(true, 'Проверяем…');
            showLoadingOnce();
            try {
                const all = await loadTests();
                const code = editor.getValue();
                const runs = [];
                for (let i = 0; i < all.length; i++) {
                    state.textContent = 'Проверяем: тест ' + (i + 1) + ' из ' + all.length + '…';
                    runs.push(await PyRunner.run(code, all[i].input, all[i].files));
                }
                state.textContent = 'Сверяем ответы…';
                const headers = { 'Content-Type': 'application/json' };
                headers[csrfHeader] = csrfToken;
                const res = await fetch(apiBase + '/check', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: headers,
                    body: JSON.stringify({
                        code: code,
                        outputs: runs.map(function (r) { return r.status === 'timeout' ? null : r.stdout; }),
                        statuses: runs.map(function (r) { return r.status; })
                    })
                });
                const json = (res.headers.get('Content-Type') || '').includes('json') ? await res.json() : null;
                if (!res.ok) {
                    showMessage(results, (json && json.error) || 'Не удалось проверить решение (код ' + res.status + ').');
                } else {
                    renderResults(json, runs);
                }
                setBusy(false);
            } catch (e) {
                showMessage(results, e.message);
                setBusy(false);
            }
        }

        runButton.addEventListener('click', run);
        checkButton.addEventListener('click', check);
        resetButton.addEventListener('click', function () {
            editor.setValue(starter);   // Ctrl+Z в редакторе вернёт прежний код
            editor.focus();
        });
    }

    // Редактор создаётся при первом раскрытии задачи: CodeMirror в свёрнутом блоке не знает своих размеров.
    document.querySelectorAll('.course-task[data-type="CODE"]').forEach(function (card) {
        let ready = false;
        function init() {
            if (card.open && !ready) {
                ready = true;
                setupTask(card);
            }
        }
        card.addEventListener('toggle', init);
        if (location.hash === '#' + card.id) {
            card.open = true;
        }
        init();
    });
})();
