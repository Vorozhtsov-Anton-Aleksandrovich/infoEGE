"""Проверка курса «Excel для ЕГЭ».

1. Структура: id уникальны, у каждой задачи есть разбор и ответ, файлы задач на месте, картинки из уроков и задач
   существуют (картинки images/todo-* — будущие скриншоты: выводятся списком, ошибкой не считаются).
2. Ответы — вторым, независимым способом (course-tools/excel/reference/manifest.json):
   - libreoffice: эталонная книга с решением формулами пересчитывается LibreOffice в headless-режиме
     (soffice --headless --convert-to xlsx), значение ячейки с именем ANSWER сравнивается с ответом из JSON;
   - translate: «во что превратится формула при копировании» — openpyxl Translator против ответа задачи;
   - manual: вопрос без вычислений (выводится списком с причиной).

LibreOffice: Windows — https://www.libreoffice.org/download/ (путь по умолчанию находится сам),
Linux — `apt install libreoffice-calc`, macOS — `brew install --cask libreoffice`. Свой путь — переменная SOFFICE.

Запуск: python course-tools/excel/validate_excel_course.py
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from decimal import Decimal
from pathlib import Path

import openpyxl
from openpyxl.formula.translate import Translator

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parents[1]
ROOT = REPO / 'src' / 'main' / 'resources' / 'courses' / 'excel-ege'
REF = TOOLS / 'reference'


def find_soffice():
    for candidate in [os.environ.get('SOFFICE'), shutil.which('soffice'), shutil.which('libreoffice'),
                      r'C:\Program Files\LibreOffice\program\soffice.exe',
                      '/Applications/LibreOffice.app/Contents/MacOS/soffice']:
        if candidate and Path(candidate).exists():
            return candidate
    return None


def recalc(files):
    """Пересчитывает книги в LibreOffice одним запуском. Возвращает папку с пересчитанными копиями."""
    soffice = find_soffice()
    if not soffice:
        sys.exit('LibreOffice не найден: установите его (см. начало файла) или задайте путь в переменной SOFFICE')
    out = Path(tempfile.mkdtemp(prefix='excel-check-'))
    profile = (out / 'profile').as_uri()   # отдельный профиль: не мешает открытому LibreOffice
    cmd = [soffice, f'-env:UserInstallation={profile}', '--headless', '--calc', '--convert-to', 'xlsx',
           '--outdir', str(out / 'calc')] + [str(f) for f in files]
    subprocess.run(cmd, capture_output=True, text=True, timeout=1800)
    return out / 'calc'


def answer_value(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    dn = wb.defined_names.get('ANSWER')
    if dn is None:
        raise ValueError('нет имени ANSWER')
    (sheet, ref), = dn.destinations
    return wb[sheet][ref.replace('$', '')].value


def same(value, task):
    """Совпадает ли значение из LibreOffice с ответом задачи (по формату ответа)."""
    expected = task['answer']
    if task['type'] == 'CHOICE':
        text = str(int(value)) if isinstance(value, float) and value.is_integer() else str(value)
        return text.strip() == task['options'][int(expected) - 1].strip()
    fmt = task.get('answerFormat', 'INT')
    if value is None:
        return False
    if fmt == 'INT':
        return isinstance(value, (int, float)) and float(value).is_integer() and int(value) == int(expected)
    if fmt == 'NUMBER':
        return abs(Decimal(str(value)) - Decimal(expected)) <= Decimal('0.005')
    if fmt == 'TEXT':
        return str(value).strip().lower() == expected.strip().lower()
    if fmt == 'INT_PAIR':
        return re.sub(r'\s+', ' ', str(value).strip()) == expected
    return str(value) == expected


def main():
    errors = []
    course = json.loads((ROOT / 'course.json').read_text(encoding='utf-8'))
    manifest = json.loads((REF / 'manifest.json').read_text(encoding='utf-8'))
    tasks = {}
    todo = set()
    for les in course['lessons']:
        md = (ROOT / les['file']).read_text(encoding='utf-8')
        texts = [md]
        data = json.loads((ROOT / les['tasks']).read_text(encoding='utf-8'))
        for t in data['tasks']:
            if t['id'] in tasks:
                errors.append(f"дубликат id {t['id']}")
            tasks[t['id']] = t
            texts.append(t['statement'] + t.get('solution', ''))
            if not t.get('solution'):
                errors.append(f"{t['id']}: нет разбора")
            if 'answer' not in t:
                errors.append(f"{t['id']}: нет ответа")
            for f in t.get('files', []):
                if not (ROOT / 'files' / f).exists():
                    errors.append(f"{t['id']}: нет файла files/{f}")
            if t['id'] not in manifest:
                errors.append(f"{t['id']}: нет второго способа проверки в manifest.json")
        for text in texts:
            for img in re.findall(r'!\[[^\]]*\]\((images/[^)]+)\)', text):
                if Path(img).name.startswith('todo-'):
                    if not (ROOT / img).exists():
                        todo.add(img)
                elif not (ROOT / img).exists():
                    errors.append(f"[{les['slug']}] нет картинки {img}")

    lo = {tid: m for tid, m in manifest.items() if m['method'] == 'libreoffice'}
    calc_dir = recalc([REF / m['file'] for m in lo.values()])
    checked = 0
    for tid, m in lo.items():
        path = calc_dir / m['file']
        if not path.exists():
            errors.append(f'{tid}: LibreOffice не пересчитал {m["file"]}')
            continue
        try:
            value = answer_value(path)
        except Exception as e:  # noqa: BLE001 — любая ошибка чтения — ошибка проверки
            errors.append(f'{tid}: {e}')
            continue
        if not same(value, tasks[tid]):
            errors.append(f"{tid}: формулы дали «{value}», а в задаче ответ «{tasks[tid]['answer']}»")
        checked += 1
    shutil.rmtree(calc_dir.parent, ignore_errors=True)

    for tid, m in manifest.items():
        if m['method'] == 'translate':
            got = Translator(m['formula'], origin=m['origin']).translate_formula(m['target']).replace('SUM', 'СУММ')
            t = tasks[tid]
            if got != t['options'][int(t['answer']) - 1]:
                errors.append(f"{tid}: Translator дал {got}, а верный вариант «{t['options'][int(t['answer']) - 1]}»")
            checked += 1

    manual = {tid: m['reason'] for tid, m in manifest.items() if m['method'] == 'manual'}
    print(f'Задач: {len(tasks)}, перепроверено вторым способом: {checked} '
          f'(LibreOffice: {len(lo)}, Translator: {sum(1 for m in manifest.values() if m["method"] == "translate")})')
    if manual:
        print('Без вычислений (проверены вручную):')
        for tid, reason in manual.items():
            print(f'  {tid}: {reason}')
    if todo:
        print(f'Скриншоты, которых ещё нет ({len(todo)}): ' + ', '.join(sorted(todo)))
    for e in errors:
        print('ОШИБКА', e)
    print(f'ошибок: {len(errors)}')
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
