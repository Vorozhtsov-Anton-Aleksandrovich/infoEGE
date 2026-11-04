// Передаёт CSRF-токен во все запросы HTMX.
document.addEventListener('htmx:configRequest', function (event) {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) {
        event.detail.headers[header.content] = token.content;
    }
});

// Подтверждение для форм с атрибутом data-confirm (без inline-обработчиков, под будущий CSP).
document.addEventListener('submit', function (event) {
    const message = event.target.dataset && event.target.dataset.confirm;
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
