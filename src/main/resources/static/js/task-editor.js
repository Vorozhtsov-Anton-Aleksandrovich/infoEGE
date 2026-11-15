// Редактор задания: панель форматирования (жирный, курсив, подчёркивание), вставка картинок
// в условие и разбор (Ctrl+V или перетаскивание файла) и переключатель «Добавить разбор».
(function () {
    'use strict';

    const form = document.getElementById('task-form');
    if (!form) {
        return;
    }
    const uploadUrl = form.dataset.imageUrl; // нет у ещё не сохранённого задания
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    // ---------- Переключатель разбора ----------

    document.querySelectorAll('[data-toggle-target]').forEach(function (checkbox) {
        const target = document.getElementById(checkbox.dataset.toggleTarget);
        checkbox.addEventListener('change', function () { target.hidden = !checkbox.checked; });
    });

    // ---------- Форматирование ----------

    // Замена текста через execCommand сохраняет историю Ctrl+Z; запасной путь — прямое присваивание.
    function replaceRange(textarea, start, end, text) {
        textarea.focus();
        textarea.setSelectionRange(start, end);
        if (!document.execCommand || !document.execCommand('insertText', false, text)) {
            textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
        }
    }

    // Сколько символов ch подряд стоит в конце (fromEnd) или в начале строки.
    function run(text, ch, fromEnd) {
        let n = 0;
        while (n < text.length && text[fromEnd ? text.length - 1 - n : n] === ch) n++;
        return n;
    }

    // Стоят ли маркеры open/close вокруг текста: before примыкает к нему слева, after — справа.
    // Для «*» учитываем, что «**» — это жирный, а «***» — жирный курсив.
    function hasMarkers(before, after, open, close) {
        if (open === '*' || open === '**') {
            const n = Math.min(run(before, '*', true), run(after, '*', false));
            return open === '**' ? n >= 2 : n % 2 === 1;
        }
        return before.endsWith(open) && after.startsWith(close);
    }

    // Оборачивает выделение в open/close; повторное нажатие снимает форматирование.
    function toggleWrap(textarea, open, close) {
        const value = textarea.value;
        let start = textarea.selectionStart;
        let end = textarea.selectionEnd;
        // Пробелы по краям выделения выносим наружу: «**слово **» Markdown не распознаёт.
        while (start < end && /\s/.test(value[start])) start++;
        while (end > start && /\s/.test(value[end - 1])) end--;
        const selected = value.slice(start, end);

        if (hasMarkers(value.slice(0, start), value.slice(end), open, close)) {
            replaceRange(textarea, start - open.length, end + close.length, selected);
            textarea.setSelectionRange(start - open.length, end - open.length);
        } else if (selected.length > open.length + close.length && hasMarkers(
                selected.slice(0, run(selected, '*', false) || open.length),
                selected.slice(selected.length - (run(selected, '*', true) || close.length)), open, close)) {
            const inner = selected.slice(open.length, selected.length - close.length);
            replaceRange(textarea, start, end, inner);
            textarea.setSelectionRange(start, start + inner.length);
        } else {
            replaceRange(textarea, start, end, open + selected + close);
            textarea.setSelectionRange(start + open.length, start + open.length + selected.length);
        }
    }

    const FORMATS = {
        bold: { open: '**', close: '**', key: 'b', label: 'Ж', title: 'Жирный (Ctrl+B)', style: 'font-weight:700' },
        italic: { open: '*', close: '*', key: 'i', label: 'К', title: 'Курсив (Ctrl+I)', style: 'font-style:italic' },
        underline: { open: '<u>', close: '</u>', key: 'u', label: 'Ч', title: 'Подчёркнутый (Ctrl+U)', style: 'text-decoration:underline' }
    };

    document.querySelectorAll('textarea[data-image-paste]').forEach(function (textarea) {
        const container = textarea.closest('.md-input');
        if (container) {
            const toolbar = document.createElement('div');
            toolbar.className = 'format-toolbar';
            toolbar.setAttribute('role', 'toolbar');
            toolbar.setAttribute('aria-label', 'Форматирование');
            Object.keys(FORMATS).forEach(function (name) {
                const f = FORMATS[name];
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = f.label;
                button.title = f.title;
                button.setAttribute('aria-label', f.title);
                button.style.cssText = f.style;
                // mousedown не забирает фокус у поля, чтобы выделение не терялось.
                button.addEventListener('mousedown', function (e) { e.preventDefault(); });
                button.addEventListener('click', function () { toggleWrap(textarea, f.open, f.close); });
                toolbar.appendChild(button);
            });
            container.insertBefore(toolbar, container.firstChild);
        }

        textarea.addEventListener('keydown', function (event) {
            if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) {
                return;
            }
            const key = event.key.toLowerCase();
            // event.code — на случай русской раскладки (Ctrl+И вместо Ctrl+B).
            const name = Object.keys(FORMATS).find(function (n) {
                return FORMATS[n].key === key || 'Key' + FORMATS[n].key.toUpperCase() === event.code;
            });
            if (name) {
                event.preventDefault();
                toggleWrap(textarea, FORMATS[name].open, FORMATS[name].close);
            }
        });
    });

    // ---------- Вставка картинок ----------

    function imageFiles(list) {
        return Array.from(list || []).filter(function (f) { return f.type.startsWith('image/'); });
    }

    function statusFor(textarea) {
        return textarea.parentElement.querySelector('.paste-status');
    }

    function setStatus(textarea, text, isError) {
        const el = statusFor(textarea);
        if (el) {
            el.textContent = text;
            el.classList.toggle('error', !!isError);
        }
    }

    // Вставляет текст в позицию курсора и сообщает htmx, что поле изменилось (обновится предпросмотр).
    function insertAtCursor(textarea, text) {
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);
        textarea.selectionStart = textarea.selectionEnd = start + text.length;
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        textarea.focus();
    }

    async function upload(textarea, files) {
        if (!uploadUrl) {
            setStatus(textarea, 'Сначала сохраните задание, затем вставьте картинку.', true);
            return;
        }
        for (const file of files) {
            setStatus(textarea, 'Загрузка картинки…', false);
            const data = new FormData();
            data.append('file', file, file.name || 'image.png');
            const headers = {};
            headers[csrfHeader] = csrfToken;
            try {
                const res = await fetch(uploadUrl, { method: 'POST', body: data, headers: headers, credentials: 'same-origin' });
                const json = res.headers.get('Content-Type') && res.headers.get('Content-Type').includes('json')
                    ? await res.json() : null;
                if (!res.ok || !json || !json.name) {
                    const message = json && json.error ? json.error
                        : res.status === 413 ? 'Файл слишком большой.' : 'Не удалось загрузить картинку.';
                    setStatus(textarea, message, true);
                    continue;
                }
                insertAtCursor(textarea, '![](file:' + json.name + ')');
                setStatus(textarea, 'Картинка «' + json.name + '» загружена и вставлена. Не забудьте сохранить задание.', false);
            } catch (e) {
                setStatus(textarea, 'Нет связи с сервером, картинка не загружена.', true);
            }
        }
    }

    document.querySelectorAll('textarea[data-image-paste]').forEach(function (textarea) {
        textarea.addEventListener('paste', function (event) {
            const files = imageFiles(event.clipboardData && event.clipboardData.files);
            if (files.length > 0) {
                event.preventDefault();
                upload(textarea, files);
            }
        });
        textarea.addEventListener('dragover', function (event) {
            if (event.dataTransfer && Array.from(event.dataTransfer.items || []).some(function (i) { return i.kind === 'file'; })) {
                event.preventDefault();
                textarea.classList.add('drop-target');
            }
        });
        textarea.addEventListener('dragleave', function () { textarea.classList.remove('drop-target'); });
        textarea.addEventListener('drop', function (event) {
            textarea.classList.remove('drop-target');
            const files = imageFiles(event.dataTransfer && event.dataTransfer.files);
            if (files.length > 0) {
                event.preventDefault();
                upload(textarea, files);
            }
        });
    });
})();
