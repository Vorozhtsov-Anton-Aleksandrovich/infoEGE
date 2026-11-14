// Web Worker (модульный): выполняет Python-код ученика в Pyodide — CPython, скомпилированный в WebAssembly.
// Сервер код не исполняет. Страница общается с воркером через py-runner.js:
//   {type: 'init', indexURL}                  → {type: 'ready'} или {type: 'init-error', error}
//   {type: 'run', runId, code, stdin, files}  → {type: 'result', runId, stdout, stderr, error, timeMs}
// Зависший код останавливает py-runner.js: terminate() воркера и запуск нового.

const MAX_OUTPUT = 100000;

// Обёртка на Python. Каждый запуск: новый словарь globals, свежий stdin из строки (EOF в конце —
// input() на пустом вводе даёт EOFError), вывод с ограничением длины, файлы прошлого запуска удаляются.
const HARNESS = `
import builtins, io, json, linecache, os, sys, traceback

_MAX_OUTPUT = ${MAX_OUTPUT}
_previous_files = []


class _LimitedOutput(io.TextIOBase):
    def __init__(self):
        self.parts = []
        self.size = 0
        self.truncated = False

    def writable(self):
        return True

    def write(self, s):
        if self.size < _MAX_OUTPUT:
            piece = s[:_MAX_OUTPUT - self.size]
            self.parts.append(piece)
            self.size += len(piece)
        if len(s) and self.size >= _MAX_OUTPUT:
            self.truncated = True
        return len(s)

    def value(self):
        text = ''.join(self.parts)
        return text + '\\n… (вывод обрезан: больше 100 000 символов)' if self.truncated else text


def __infoege_run(code, stdin, files_json):
    for name in _previous_files:
        try:
            os.remove(name)
        except OSError:
            pass
    _previous_files.clear()
    for name, content in json.loads(files_json).items():
        with open(name, 'w', encoding='utf-8') as f:
            f.write(content)
        _previous_files.append(name)

    # Исходник для traceback: так в сообщении об ошибке видна сама строка кода.
    linecache.cache['<main>'] = (len(code), None, code.splitlines(True), '<main>')
    out, err = _LimitedOutput(), _LimitedOutput()
    saved = sys.stdin, sys.stdout, sys.stderr
    sys.stdin, sys.stdout, sys.stderr = io.StringIO(stdin), out, err
    error = None
    try:
        exec(compile(code, '<main>', 'exec'), {'__name__': '__main__', '__builtins__': builtins})
    except SystemExit:
        pass
    except BaseException as e:
        tb = e.__traceback__
        while tb is not None and tb.tb_frame.f_code.co_filename != '<main>':
            tb = tb.tb_next
        error = ''.join(traceback.format_exception(type(e), e, tb))
    finally:
        sys.stdin, sys.stdout, sys.stderr = saved
    return out.value(), err.value(), error
`;

let runHarness = null;

async function init(indexURL) {
    const { loadPyodide } = await import(indexURL + 'pyodide.mjs');
    const pyodide = await loadPyodide({ indexURL });
    pyodide.runPython(HARNESS);
    runHarness = pyodide.globals.get('__infoege_run');
}

function run(msg) {
    const started = performance.now();
    const result = runHarness(msg.code, msg.stdin || '', JSON.stringify(msg.files || {}));
    const [stdout, stderr, error] = result.toJs();
    result.destroy();
    return {
        type: 'result',
        runId: msg.runId,
        stdout: stdout || '',
        stderr: stderr || '',
        error: error || null,
        timeMs: Math.round(performance.now() - started)
    };
}

self.onmessage = async function (event) {
    const msg = event.data;
    if (msg.type === 'init') {
        try {
            await init(msg.indexURL);
            self.postMessage({ type: 'ready' });
        } catch (e) {
            self.postMessage({ type: 'init-error', error: String((e && e.message) || e) });
        }
    } else if (msg.type === 'run') {
        try {
            self.postMessage(run(msg));
        } catch (e) {
            self.postMessage({ type: 'result', runId: msg.runId, stdout: '', stderr: '',
                error: 'Внутренняя ошибка запуска: ' + String((e && e.message) || e), timeMs: 0 });
        }
    }
};
