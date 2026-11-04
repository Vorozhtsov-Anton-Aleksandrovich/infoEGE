// Редактор задания: вставка картинок в условие и разбор (Ctrl+V или перетаскивание файла)
// и переключатель «Добавить разбор».
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
