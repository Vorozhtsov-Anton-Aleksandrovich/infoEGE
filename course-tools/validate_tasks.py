"""Проверяет все курсы в src/main/resources/courses/:
- файлы уроков и задач на месте, картинки из уроков и задач существуют;
- id задач уникальны во всех курсах;
- задачи CODE: есть открытый тест, эталонное решение course-tools/solutions/<id>.py проходит все тесты;
- задачи без кода: ответ подходит к типу задачи, есть разбор;
- виджеты :::widget известны, ссылки :::python ведут на существующие задачи.
Запускать после ручной правки JSON задач, уроков или решений.

Запуск: python course-tools/validate_tasks.py
"""
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
COURSES = REPO / 'src' / 'main' / 'resources' / 'courses'
SOL_DIR = REPO / 'course-tools' / 'solutions'
JAVA_MD = REPO / 'src' / 'main' / 'java' / 'com' / 'github' / 'anton' / 'infoege' / 'course' / 'CourseMarkdown.java'


def normalize(out):
    return '\n'.join(l.rstrip() for l in out.rstrip().splitlines())


def run(code, inp, files):
    with tempfile.TemporaryDirectory() as d:
        for name, content in (files or {}).items():
            Path(d, name).write_text(content, encoding='utf-8')
        p = Path(d, 'main.py')
        p.write_text(code, encoding='utf-8')
        r = subprocess.run([sys.executable, '-I', '-X', 'utf8', str(p)], input=inp, capture_output=True, text=True,
                           cwd=d, timeout=30, encoding='utf-8')
        return r.returncode, r.stdout, r.stderr


def known_widgets():
    text = JAVA_MD.read_text(encoding='utf-8')
    block = text[text.index('WIDGETS = Set.of('):text.index(');', text.index('WIDGETS = Set.of('))]
    return set(re.findall(r'"([a-z0-9-]+)"', block))


def check_answer(t, root):
    """Ошибки формата ответа задачи без кода (та же логика, что в CourseRegistry)."""
    typ = t['type']
    errs = []
    answers = list(t.get('answers') or []) + ([t['answer']] if 'answer' in t else [])
    if not answers:
        return ['нет answers']
    if not t.get('solution'):
        errs.append('нет разбора (solution)')
    for a in answers:
        a = re.sub(r'\s+', '', a)
        if typ == 'CHOICE':
            n = len(t.get('options') or [])
            if n < 2 or not a.isdigit() or not 1 <= int(a) <= n:
                errs.append(f'ответ CHOICE «{a}» вне 1..{n}')
        elif typ == 'TRUTH_TABLE':
            n = len(t.get('variables') or [])
            if not re.fullmatch('[01]+', a) or len(a) != 2 ** n:
                errs.append(f'ответ TRUTH_TABLE должен быть {2 ** n} символов 0/1')
        elif typ == 'MATCH_GRAPH':
            parts = a.split(',')
            n = len(t.get('variables') or [])
            m = t.get('numbers') or 0
            if len(parts) != n or not all(p.isdigit() and 1 <= int(p) <= m for p in parts):
                errs.append('ответ MATCH_GRAPH не подходит к вершинам')
        elif typ == 'ANSWER':
            fmt = t.get('answerFormat', 'FREE')
            if fmt == 'INT' and not re.fullmatch(r'[+-]?\d+', a):
                errs.append(f'ответ «{a}» не целое число')
            elif fmt == 'INT_PAIR' and not re.fullmatch(r'[+-]?\d+ [+-]?\d+', t['answer'].strip() if 'answer' in t else a):
                errs.append(f'ответ «{a}» — не два целых через пробел')
            elif fmt == 'NUMBER' and not re.fullmatch(r'[+-]?\d+([.,]\d+)?', a):
                errs.append(f'ответ «{a}» не число')
        else:
            errs.append(f'неизвестный type {typ}')
    for f in t.get('files', []):
        if not (root / "files" / f).exists():
            errs.append(f'нет файла files/{f}')
    return errs


def main():
    errors = 0
    widgets = known_widgets()
    all_tasks = {}       # (курс, урок, id)
    links = []
    summary = []
    ids = set()
    for manifest in sorted(COURSES.glob('*/course.json')):
        root = manifest.parent
        course = json.loads(manifest.read_text(encoding='utf-8'))
        tasks_total = tests_total = 0
        for les in course['lessons']:
            md = (root / les['file']).read_text(encoding='utf-8')
            for img in re.findall(r'!\[[^\]]*\]\(([^)]+)\)', md):
                # images/todo-* — будущие скриншоты: пока файла нет, в уроке они не выводятся (SCREENSHOTS_TODO.md)
                if not (root / img).exists() and not Path(img).name.startswith('todo-'):
                    print(f"[{course['slug']}/{les['slug']}] нет картинки {img}")
                    errors += 1
            for name in re.findall(r'^:::widget\s+([a-z0-9-]+)', md, re.M):
                if name not in widgets:
                    print(f"[{course['slug']}/{les['slug']}] неизвестный виджет {name}")
                    errors += 1
            for c, l, tid in re.findall(r'^:::python\s+([a-z0-9-]+)/([a-z0-9-]+)/(\S+)', md, re.M):
                links.append((f"{course['slug']}/{les['slug']}", c, l, tid))
            data = json.loads((root / les['tasks']).read_text(encoding='utf-8'))
            for t in data['tasks']:
                tasks_total += 1
                all_tasks[(course['slug'], les['slug'], t['id'])] = True
                if t['id'] in ids:
                    print(f"дубликат id {t['id']}")
                    errors += 1
                ids.add(t['id'])
                text = t.get('statement', '') + t.get('solution', '')
                for img in re.findall(r'!\[[^\]]*\]\(([^)]+)\)', text):
                    if not (root / img).exists():
                        print(f"{t['id']}: нет картинки {img}")
                        errors += 1
                if t.get('type', 'CODE') != 'CODE':
                    for e in check_answer(t, root):
                        print(f"{t['id']}: {e}")
                        errors += 1
                    continue
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
        summary.append(f"{course['slug']}: уроков {len(course['lessons'])}, задач {tasks_total}, тестов {tests_total}")
    for where, c, l, tid in links:
        if (c, l, tid) not in all_tasks:
            print(f'[{where}] ссылка :::python на несуществующую задачу {c}/{l}/{tid}')
            errors += 1
    for line in summary:
        print(line)
    print(f'ошибок: {errors}')
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
