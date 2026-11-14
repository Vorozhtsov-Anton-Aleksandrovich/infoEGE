"""Проверяет курс: все файлы на месте, картинки из уроков существуют,
каждое эталонное решение проходит все тесты своей задачи.
Запускать после ручной правки JSON задач или решений.

Запуск: python course-tools/validate_tasks.py
"""
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ROOT = REPO / 'src' / 'main' / 'resources' / 'courses' / 'python-ege'
SOL_DIR = REPO / 'course-tools' / 'solutions'


def normalize(out):
    return '\n'.join(l.rstrip() for l in out.rstrip().splitlines())


def run(code, inp, files):
    with tempfile.TemporaryDirectory() as d:
        for name, content in (files or {}).items():
            Path(d, name).write_text(content, encoding='utf-8')
        p = Path(d, 'main.py')
        p.write_text(code, encoding='utf-8')
        r = subprocess.run([sys.executable, '-I', str(p)], input=inp, capture_output=True, text=True,
                           cwd=d, timeout=30, encoding='utf-8')
        return r.returncode, r.stdout, r.stderr


def main():
    course = json.loads((ROOT / 'course.json').read_text(encoding='utf-8'))
    errors = 0
    tasks_total = tests_total = 0
    ids = set()
    for les in course['lessons']:
        md = (ROOT / les['file']).read_text(encoding='utf-8')
        for img in re.findall(r'!\[[^\]]*\]\(([^)]+)\)', md):
            if not (ROOT / img).exists():
                print(f"[{les['slug']}] нет картинки {img}")
                errors += 1
        data = json.loads((ROOT / les['tasks']).read_text(encoding='utf-8'))
        for t in data['tasks']:
            tasks_total += 1
            if t['id'] in ids:
                print(f"дубликат id {t['id']}")
                errors += 1
            ids.add(t['id'])
            if not any(not x['hidden'] for x in t['tests']):
                print(f"{t['id']}: нет ни одного открытого теста")
                errors += 1
            code = (SOL_DIR / f"{t['id']}.py").read_text(encoding='utf-8')
            for i, test in enumerate(t['tests'], 1):
                tests_total += 1
                rc, out, err = run(code, test['input'], test.get('files'))
                if rc != 0 or normalize(out) != test['output']:
                    print(f"{t['id']} тест {i}: FAIL\n{err[-500:]}")
                    errors += 1
    print(f'Уроков: {len(course["lessons"])}, задач: {tasks_total}, тестов: {tests_total}, ошибок: {errors}')
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
