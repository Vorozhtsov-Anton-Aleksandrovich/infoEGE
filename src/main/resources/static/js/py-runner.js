// Запуск Python-кода в воркере (py-worker.js) с очередью и таймаутом.
// PyRunner.run(code, stdin, files) → Promise<{status: 'ok'|'error'|'timeout', stdout, stderr, error, timeMs}>.
// При таймауте воркер уничтожается и создаётся заново (Python загрузится повторно, уже из кэша браузера).
(function () {
    'use strict';

    const TIMEOUT_MS = 5000;
    const main = document.querySelector('[data-pyodide-url]');
    if (!main) {
        return;
    }
    const indexURL = main.dataset.pyodideUrl;
    const workerURL = main.dataset.pyWorker;

    let worker = null;
    let ready = null;
    let loaded = false;
    let nextRunId = 1;
    let queue = Promise.resolve();
    const loadingListeners = [];

    function start() {
        worker = new Worker(workerURL, { type: 'module' });
        ready = new Promise(function (resolve, reject) {
            worker.onmessage = function (e) {
                if (e.data.type === 'ready') {
                    loaded = true;
                    resolve();
                } else if (e.data.type === 'init-error') {
                    reject(new Error(e.data.error));
                }
            };
            worker.onerror = function (e) {
                reject(new Error(e.message || 'не удалось запустить воркер'));
            };
        });
        worker.postMessage({ type: 'init', indexURL: indexURL });
    }

    function stop() {
        if (worker) {
            worker.terminate();
        }
        worker = null;
        ready = null;
    }

    async function runNow(code, stdin, files) {
        if (!worker) {
            if (!loaded) {
                loadingListeners.forEach(function (fn) { fn(); });
            }
            start();
        }
        try {
            await ready;
        } catch (e) {
            stop();
            throw new Error('Не удалось загрузить Python: ' + e.message);
        }
        return new Promise(function (resolve) {
            const runId = nextRunId++;
            const timer = setTimeout(function () {
                stop();
                resolve({ status: 'timeout', stdout: '', stderr: '', error: null, timeMs: TIMEOUT_MS });
            }, TIMEOUT_MS);
            worker.onmessage = function (e) {
                if (e.data.type === 'result' && e.data.runId === runId) {
                    clearTimeout(timer);
                    resolve({
                        status: e.data.error ? 'error' : 'ok',
                        stdout: e.data.stdout,
                        stderr: e.data.stderr,
                        error: e.data.error,
                        timeMs: e.data.timeMs
                    });
                }
            };
            worker.postMessage({ type: 'run', runId: runId, code: code, stdin: stdin, files: files || {} });
        });
    }

    window.PyRunner = {
        TIMEOUT_MS: TIMEOUT_MS,
        /** Запуски выполняются строго по очереди. */
        run: function (code, stdin, files) {
            const job = queue.then(function () { return runNow(code, stdin, files); });
            queue = job.catch(function () {});
            return job;
        },
        isLoaded: function () { return loaded; },
        /** fn вызывается, когда начинается первая (долгая) загрузка Python. */
        onLoading: function (fn) { loadingListeners.push(fn); }
    };
})();
