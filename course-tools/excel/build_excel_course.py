"""Задачи курса «Excel для ЕГЭ». Генерирует детерминированно (random.Random(seed)):
  src/main/resources/courses/excel-ege/files/*.xlsx  — файлы к задачам (их скачивают ученики)
  src/main/resources/courses/excel-ege/tasks/*.json  — задачи; ответ посчитан здесь же на Python
  course-tools/excel/reference/<id>.xlsx            — копия файла задачи + решение ФОРМУЛАМИ (ячейка с именем ANSWER)
  course-tools/excel/reference/manifest.json        — как перепроверять каждую задачу (см. validate_excel_course.py)

Ответы руками не пишутся: Python-решение здесь, независимое решение формулами — в reference, его пересчитывает
LibreOffice в validate_excel_course.py. Формулы в reference — те же, что в разборе задачи.

Запуск: python course-tools/excel/build_excel_course.py
"""
import json
import random
import re
import shutil
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import openpyxl
from openpyxl.styles import Border, Font, Side
from openpyxl.utils import get_column_letter
from openpyxl.workbook.defined_name import DefinedName

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parents[1]
ROOT = REPO / 'src' / 'main' / 'resources' / 'courses' / 'excel-ege'
FILES = ROOT / 'files'
TASKS = ROOT / 'tasks'
REF = TOOLS / 'reference'

LESSONS = []
MANIFEST = {}
BOLD = Font(bold=True)


# ============================================================================ общие помощники

def lesson(slug):
    les = {'slug': slug, 'tasks': []}
    LESSONS.append(les)
    return les


def task(les, id_, title, statement, answer, solution, fmt='INT', files=(), hint='', options=None, type_='ANSWER'):
    t = {'id': id_, 'type': type_, 'title': title, 'statement': statement.strip()}
    if files:
        t['files'] = list(files)
    if type_ == 'ANSWER':
        t['answer'] = str(answer)
        t['answerFormat'] = fmt
    else:
        t['options'] = options
        t['answer'] = str(answer)
    if hint:
        t['hint'] = hint
    t['solution'] = solution.strip()
    les['tasks'].append(t)


def write_book(path, sheets):
    """sheets: [(название, заголовок или None, строки)]. Возвращает книгу (уже сохранена)."""
    wb = openpyxl.Workbook()
    wb.remove(wb.active)
    for title, header, rows in sheets:
        ws = wb.create_sheet(title)
        r0 = 1
        if header:
            for j, h in enumerate(header, 1):
                ws.cell(1, j, h).font = BOLD
            r0 = 2
        for i, row in enumerate(rows):
            for j, v in enumerate(row, 1):
                cell = ws.cell(r0 + i, j, v)
                if isinstance(v, date):
                    cell.number_format = 'DD.MM.YYYY'
        for j in range(1, (len(header) if header else max((len(r) for r in rows), default=1)) + 1):
            ws.column_dimensions[get_column_letter(j)].width = 14
    path.parent.mkdir(parents=True, exist_ok=True)
    wb.save(path)
    return wb


def data_file(name, sheets):
    write_book(FILES / name, sheets)
    return name


def reference(task_id, build, data=None, check='value'):
    """Эталонная книга: копия файла задачи (или пустая книга) + формулы. build(wb) возвращает формулу ответа,
    она пишется на лист «Решение» в B1 с именем ANSWER. check: value — сравнить с ответом задачи;
    option — сравнить с текстом правильного варианта CHOICE."""
    wb = openpyxl.load_workbook(FILES / data) if data else openpyxl.Workbook()
    formula = build(wb)
    ws = wb.create_sheet('Решение')
    ws['A1'] = 'Ответ'
    ws['B1'] = formula
    wb.defined_names['ANSWER'] = DefinedName('ANSWER', attr_text="'Решение'!$B$1")
    REF.mkdir(parents=True, exist_ok=True)
    wb.save(REF / f'{task_id}.xlsx')
    MANIFEST[task_id] = {'method': 'libreoffice', 'file': f'{task_id}.xlsx', 'check': check}


def _helper(ws, col, r1, r2, formula):
    """Вспомогательный столбец: формула formula(номер строки) в строках r1..r2."""
    for r in range(r1, r2 + 1):
        ws[f'{col}{r}'] = formula(r)


def manual(task_id, reason):
    MANIFEST[task_id] = {'method': 'manual', 'reason': reason}


def round2(x):
    return float(Decimal(str(x)).quantize(Decimal('0.01'), ROUND_HALF_UP))


def round_half_up(x):
    return int(Decimal(str(x)).quantize(Decimal('1'), ROUND_HALF_UP))


def fmt_num(x):
    """Число для ответа NUMBER: без лишних нулей, десятичная точка."""
    s = f'{x:.2f}'.rstrip('0').rstrip('.')
    return s


def ru(n):
    """1234567 → «1 234 567» для текста разборов."""
    return f'{n:,}'.replace(',', ' ')


SURNAMES = ['Иванов', 'Петров', 'Сидоров', 'Смирнов', 'Кузнецов', 'Попов', 'Васильев', 'Соколов', 'Михайлов', 'Новиков',
            'Фёдоров', 'Морозов', 'Волков', 'Алексеев', 'Лебедев', 'Семёнов', 'Егоров', 'Павлов', 'Козлов', 'Степанов',
            'Николаев', 'Орлов', 'Андреев', 'Макаров', 'Никитин', 'Захаров', 'Зайцев', 'Соловьёв', 'Борисов', 'Яковлев',
            'Григорьев', 'Романов', 'Воробьёв', 'Сергеев', 'Антонов', 'Анисимов', 'Андрианов', 'Фролов', 'Белов',
            'Тарасов', 'Белоусов', 'Комаров', 'Кириллов', 'Ильин', 'Гусев', 'Титов', 'Кудрявцев', 'Баранов', 'Куликов',
            'Анохин', 'Ершов', 'Ковалёв', 'Ефимов', 'Жуков', 'Карпов', 'Абрамов', 'Медведев', 'Денисов', 'Чернов']
NAMES = ['Аня', 'Борис', 'Вера', 'Глеб', 'Даша', 'Егор', 'Женя', 'Зоя', 'Илья', 'Катя', 'Лёша', 'Маша', 'Никита', 'Оля',
         'Паша', 'Рита', 'Саша', 'Таня', 'Ульяна', 'Федя', 'Юля', 'Яна']


def person(rnd):
    return f'{rnd.choice(SURNAMES)} {rnd.choice(NAMES)}'


# ============================================================================ Урок 1
L = lesson('01-basics')

task(L, 'xl01-1', 'Адрес ячейки',
     'На картинке активная ячейка обведена зелёной рамкой. Какой у неё адрес?\n\n![Лист](images/xl01-1-active.svg)',
     1, '''Активная ячейка стоит в столбце **C** (буква над ней подсвечена) и в строке **4** (номер слева подсвечен).
Адрес — это буква столбца и номер строки: **C4**. Тот же адрес написан в поле имени слева от строки формул.''',
     type_='CHOICE', options=['C4', '4C', 'D3', 'C3'])
manual('xl01-1', 'адрес на картинке задаётся в gen_excel_images.py: s.render(active="C4")')

task(L, 'xl01-2', 'Сколько ячеек в диапазоне',
     'Сколько ячеек в диапазоне **B2:D10**?', 3 * 9,
     '''Диапазон B2:D10 — прямоугольник от B2 до D10.

1. Столбцы: B, C, D — **3** столбца.
2. Строки: со 2-й по 10-ю — 10 − 2 + 1 = **9** строк (не 8! обе границы входят).
3. Ячеек: 3 · 9 = **27**.

Проверка в Excel: выдели B2:D10 — в поле имени во время выделения видно «9R x 3C», а функция
`=ЧСТРОК(B2:D10)*ЧИСЛСТОЛБ(B2:D10)` вернёт 27.''',
     hint='Посчитай столбцы и строки отдельно. Строк со 2-й по 10-ю — 9.')
reference('xl01-2', lambda wb: '=ROWS(B2:D10)*COLUMNS(B2:D10)')

rnd = random.Random(101)
grid101 = [[rnd.randint(1, 999) for _ in range(8)] for _ in range(300)]
f = data_file('xl01-3.xlsx', [('Лист1', None, grid101)])
task(L, 'xl01-3', 'Найди значение в ячейке',
     'Скачай файл. В нём 300 строк чисел. Какое число записано в ячейке **F137**?',
     grid101[136][5],
     f'''Прокручивать до 137-й строки долго. Быстрее так:

1. Щёлкни в **поле имени** (слева от строки формул).
2. Набери `F137` и нажми **Enter** — Excel сам перейдёт к ячейке.
3. В ячейке и в строке формул видно число **{grid101[136][5]}**.

Ещё способ: в любой пустой ячейке напиши формулу `=F137`.''',
     files=[f], hint='Впиши адрес F137 в поле имени и нажми Enter.')
reference('xl01-3', lambda wb: "='Лист1'!F137", f)

rnd = random.Random(102)
people = []
while len(people) < 1234:
    p = person(rnd)
    people.append(p)
target_row = 857
target = 'Анохин Илья'
people = [p for p in people if p != target]
people.insert(target_row - 2, target)   # строка 857 (строка 1 — заголовок)
people = people[:1234]
f = data_file('xl01-4.xlsx', [('Лист1', ['Ученик'], [[p] for p in people])])
task(L, 'xl01-4', 'Сколько строк заполнено',
     'Скачай файл. В столбце A — заголовок «Ученик» и список учеников. Сколько учеников в списке '
     '(заголовок не считается)?', len(people),
     f'''1. Встань в ячейку A1 и нажми **Ctrl + ↓** — курсор прыгнет на последнюю заполненную ячейку столбца: **A{len(people) + 1}**.
2. Номер последней строки — {len(people) + 1}, но первая строка — заголовок. Учеников: {len(people) + 1} − 1 = **{len(people)}**.

Формулой: `=СЧЁТЗ(A:A)-1` — **СЧЁТЗ** считает непустые ячейки, минус заголовок.''',
     files=[f], hint='Ctrl + ↓ переносит на последнюю заполненную ячейку столбца.')
reference('xl01-4', lambda wb: "=COUNTA('Лист1'!A:A)-1", f)

assert people.count(target) == 1
row_target = people.index(target) + 2
task(L, 'xl01-5', 'Найди строку',
     f'В том же файле найди ученика «{target}». В какой **строке** листа он записан?', row_target,
     f'''1. Нажми **Ctrl + F** (Найти), введи «{target}», нажми «Найти далее».
2. Excel выделит ячейку **A{row_target}** — номер строки виден слева и в поле имени.

Формулой: `=ПОИСКПОЗ("{target}";A:A;0)` — вернёт номер строки {row_target}.''',
     files=['xl01-4.xlsx'], hint='Ctrl + F открывает поиск.')
reference('xl01-5', lambda wb: f"=MATCH(\"{target}\",'Лист1'!A:A,0)", 'xl01-4.xlsx')

rnd = random.Random(103)
cols103 = [[rnd.randint(10, 500), rnd.randint(1, 99), rnd.randint(100, 9999)] for _ in range(500)]
f = data_file('xl01-6.xlsx', [('Лист1', ['Цена', 'Скидка', 'Выручка'], cols103)])
task(L, 'xl01-6', 'Сумма из строки состояния',
     'Скачай файл. Найди сумму всех чисел в столбце **C** «Выручка». Формулы писать не нужно: '
     'выдели столбец и посмотри вниз, в строку состояния.', sum(r[2] for r in cols103),
     f'''1. Нажми на букву **C** над столбцом — выделится весь столбец.
2. Внизу окна, в **строке состояния**, Excel показывает «Среднее», «Количество» и «Сумма» выделенных чисел.
3. Сумма: **{sum(r[2] for r in cols103)}** (заголовок — текст, в сумму не входит).

Формулой: `=СУММ(C:C)`.''',
     files=[f], hint='Выдели столбец C и посмотри в правый нижний угол окна.')
reference('xl01-6', lambda wb: "=SUM('Лист1'!C:C)", f)

task(L, 'xl01-7', 'Горячая клавиша',
     'Курсор стоит в A1, ниже — тысячи заполненных строк. Какое сочетание клавиш переносит курсор на последнюю '
     'заполненную ячейку столбца?', 2,
     '''**Ctrl + ↓** — «прыжок» до конца блока данных вниз. Ctrl + Home возвращает в A1, Ctrl + End — в правый нижний
угол всех данных листа, Ctrl + Shift + ↓ не только прыгает, но и **выделяет** всё по пути.''',
     type_='CHOICE', options=['Ctrl + Home', 'Ctrl + ↓', 'Shift + ↓', 'Ctrl + Z'])
manual('xl01-7', 'вопрос о сочетании клавиш, вычислять нечего')

# ============================================================================ Урок 2
L = lesson('02-formulas')

rnd = random.Random(201)
d201 = [[rnd.randint(10, 500), rnd.randint(1, 20)] for _ in range(250)]
f = data_file('xl02-1.xlsx', [('Лист1', ['Цена', 'Количество'], d201)])
ans = sum(a * b for a, b in d201)
task(L, 'xl02-1', 'Стоимость покупок',
     'Скачай файл: в столбце A — цена товара, в столбце B — количество. В столбце C посчитай стоимость '
     '(цена × количество) для каждой строки и найди **общую стоимость** всех покупок.', ans,
     f'''1. В C1 напиши заголовок «Стоимость».
2. В **C2** напиши формулу `=A2*B2` и нажми Enter.
3. Выдели C2 и **дважды щёлкни** по маркеру в правом нижнем углу — формула протянется до последней строки (C251).
4. В пустой ячейке: `=СУММ(C2:C251)` → **{ans}**.''',
     files=[f], hint='Формула в C2: =A2*B2. Потом протяни её вниз и сложи столбец.')
reference('xl02-1', lambda wb: (_helper(wb['Лист1'], 'C', 2, 251, lambda r: f'=A{r}*B{r}'), '=SUM(Лист1!C2:C251)')[1], f)


task(L, 'xl02-2', 'Порядок действий',
     'Что покажет ячейка с формулой `=2+3*4^2`?', 1,
     '''Excel считает как в математике: сначала степень `^`, потом умножение, потом сложение.

1. 4^2 = 16
2. 3 · 16 = 48
3. 2 + 48 = **50**''',
     type_='CHOICE', options=['50', '400', '196', '14'])
reference('xl02-2', lambda wb: '=2+3*4^2', check='option')

task(L, 'xl02-3', 'Ошибка #ДЕЛ/0!',
     'В ячейке C5 формула `=A5/B5`, а в ячейке видно **#ДЕЛ/0!**. Что это значит?', 3,
     '''**#ДЕЛ/0!** — деление на ноль. Значит, в B5 записан 0 или B5 **пустая** (пустая ячейка в формуле считается нулём).''',
     type_='CHOICE', options=['В A5 записан текст', 'Опечатка в формуле', 'B5 равна нулю или пустая', 'Число не помещается в ячейку'])
manual('xl02-3', 'вопрос о смысле сообщения об ошибке')

task(L, 'xl02-4', 'Правильная формула',
     'В A1 записано 7, в B1 — 5. Какая запись в C1 даст **12**?', 4,
     '''Формула обязательно начинается со знака **=**, адреса пишутся латинскими буквами без кавычек.

- `A1+B1` без «=» — это просто текст.
- `="A1+B1"` — текст в кавычках, Excel покажет «A1+B1».
- `=А1+В1` с **русскими** А и В — ошибка #ИМЯ?, таких адресов нет.
- `=A1+B1` — правильно, 12.''',
     type_='CHOICE', options=['A1+B1', '="A1+B1"', '=А1+В1 (русские буквы)', '=A1+B1'])
manual('xl02-4', 'вопрос о записи формулы')

rnd = random.Random(205)
d205 = [[rnd.randint(1, 100), rnd.randint(1, 100)] for _ in range(300)]
f = data_file('xl02-5.xlsx', [('Лист1', ['a', 'b'], d205)])
ans = sum((a - b) * 2 + a for a, b in d205)
task(L, 'xl02-5', 'Формула со скобками',
     'В файле два столбца чисел a и b. Для каждой строки посчитай в столбце C значение **(a − b) · 2 + a** и найди '
     'сумму всех полученных значений.', ans,
     f'''1. В **C2**: `=(A2-B2)*2+A2`. Скобки обязательны: без них `A2-B2*2` посчитается как A2 − (B2·2).
2. Протяни формулу до C301.
3. `=СУММ(C2:C301)` → **{ans}**.''',
     files=[f])
reference('xl02-5', lambda wb: (_helper(wb['Лист1'], 'C', 2, 301, lambda r: f'=(A{r}-B{r})*2+A{r}'), '=SUM(Лист1!C2:C301)')[1], f)

rnd = random.Random(206)
d206 = [[rnd.randrange(-30, 41, 5)] for _ in range(200)]
f = data_file('xl02-6.xlsx', [('Лист1', ['Цельсий'], d206)])
ans = max(c * 9 // 5 + 32 for (c,) in d206)
task(L, 'xl02-6', 'Градусы Фаренгейта',
     'В файле — температуры в градусах Цельсия. Переведи каждую в градусы Фаренгейта по формуле **F = C · 9 / 5 + 32** '
     'и найди наибольшее значение в Фаренгейтах.', ans,
     f'''1. В **B2**: `=A2*9/5+32`, протяни вниз до B201.
2. Наибольшее: `=МАКС(B2:B201)` → **{ans}**.

Проверка: наибольшая температура в Цельсиях — {max(c for (c,) in d206)}, и {max(c for (c,) in d206)} · 9 / 5 + 32 = {ans}.''',
     files=[f])
reference('xl02-6', lambda wb: (_helper(wb['Лист1'], 'B', 2, 201, lambda r: f'=A{r}*9/5+32'), '=MAX(Лист1!B2:B201)')[1], f)

rnd = random.Random(207)
d207 = [[rnd.randint(1, 50), rnd.randint(1, 50)] for _ in range(180)]
f = data_file('xl02-7.xlsx', [('Лист1', ['Ширина', 'Высота'], d207)])
s_area = sum(a * b for a, b in d207)
s_per = sum(2 * (a + b) for a, b in d207)
task(L, 'xl02-7', 'Площади и периметры',
     'В файле — размеры прямоугольников. Посчитай в столбце C площадь каждого, в столбце D — периметр. '
     'Найди разность: **сумма всех площадей − сумма всех периметров**.', s_area - s_per,
     f'''1. **C2**: `=A2*B2` (площадь), **D2**: `=2*(A2+B2)` (периметр). Протяни обе формулы до строки 181.
2. Сумма площадей `=СУММ(C2:C181)` = {s_area}, сумма периметров `=СУММ(D2:D181)` = {s_per}.
3. Разность: {s_area} − {s_per} = **{s_area - s_per}**.''',
     files=[f], hint='Две вспомогательные колонки, две суммы, одна разность.')


def _ref207(wb):
    ws = wb['Лист1']
    _helper(ws, 'C', 2, 181, lambda r: f'=A{r}*B{r}')
    _helper(ws, 'D', 2, 181, lambda r: f'=2*(A{r}+B{r})')
    return '=SUM(Лист1!C2:C181)-SUM(Лист1!D2:D181)'


reference('xl02-7', _ref207, f)

# ============================================================================ Урок 3
L = lesson('03-references')


def col_index(letters):
    n = 0
    for ch in letters:
        n = n * 26 + ord(ch) - 64
    return n


def shift_formula(formula, origin, target):
    """Своя реализация копирования формулы (первый способ). Второй — openpyxl Translator в валидаторе."""
    o_col, o_row = re.fullmatch(r'([A-Z]+)(\d+)', origin).groups()
    t_col, t_row = re.fullmatch(r'([A-Z]+)(\d+)', target).groups()
    dc = col_index(t_col) - col_index(o_col)
    dr = int(t_row) - int(o_row)

    def repl(m):
        cabs, col, rabs, row = m.groups()
        if not cabs:
            col = get_column_letter(col_index(col) + dc)
        if not rabs:
            row = str(int(row) + dr)
        return f'{cabs}{col}{rabs}{row}'
    return re.sub(r'(\$?)([A-Z]+)(\$?)(\d+)', repl, formula)


def copy_choice(id_, title, formula, origin, target, distractors, story):
    right = shift_formula(formula, origin, target)
    show = lambda f_: f_.replace('SUM', 'СУММ')
    options = [show(right)] + [show(d) for d in distractors]
    order = random.Random(id_).sample(range(len(options)), len(options))
    shuffled = [options[i] for i in order]
    correct = shuffled.index(show(right)) + 1
    (oc, orow), (tc, trow) = [re.fullmatch(r'([A-Z]+)(\d+)', a).groups() for a in (origin, target)]
    dc, dr = col_index(tc) - col_index(oc), int(trow) - int(orow)
    task(L, id_, title, f'{story}\n\nКакой станет формула?', correct,
         f'''Считаем сдвиг: из {origin} в {target} — на {dc} столбц. вправо и на {dr} строк. вниз.

- части ссылки **без $** сдвигаются на столько же;
- части **с $** стоят на месте.

`{show(formula)}` → `{show(right)}`.''',
         type_='CHOICE', options=shuffled)
    MANIFEST[id_] = {'method': 'translate', 'formula': formula, 'origin': origin, 'target': target}


copy_choice('xl03-1', 'Копирование: относительные ссылки', '=A1+$B2', 'C3', 'E5',
            ['=C3+$D4', '=A1+$B2', '=C3+$B2'],
            'В ячейке **C3** записана формула `=A1+$B2`. Её скопировали в ячейку **E5**.')
copy_choice('xl03-2', 'Копирование: смешанные ссылки', '=$A$1*B$2', 'D4', 'F7',
            ['=$A$1*D$5', '=$C$4*D$2', '=$A$1*B$2'],
            'В ячейке **D4** записана формула `=$A$1*B$2`. Её скопировали в ячейку **F7**.')
copy_choice('xl03-3', 'Копирование: функция', '=SUM($A1:B$3)', 'C5', 'D6',
            ['=SUM($A2:C$4)', '=SUM($B2:C$3)', '=SUM($A1:B$3)'],
            'В ячейке **C5** записана формула `=СУММ($A1:B$3)`. Её скопировали в ячейку **D6**.')

rnd = random.Random(304)
d304 = [[f'Магазин {i + 1}', rnd.randint(50_000, 900_000)] for i in range(100)]
f = data_file('xl03-4.xlsx', [('Лист1', ['Магазин', 'Выручка'], d304)])
total = sum(r[1] for r in d304)
ans = round2(max(r[1] for r in d304) / total * 100)
task(L, 'xl03-4', 'Доля от общей суммы',
     'В файле — выручка 100 магазинов. Для каждого магазина посчитай его **долю** в общей выручке. Какова наибольшая '
     'доля **в процентах**? Ответ округли до сотых.', fmt_num(ans),
     f'''1. В **C2**: `=B2/СУММ($B$2:$B$101)`. Знаки $ нужны, чтобы при копировании вниз диапазон суммы **не съезжал**.
2. Протяни до C101.
3. Наибольшая доля в процентах: `=ОКРУГЛ(МАКС(C2:C101)*100;2)` → **{fmt_num(ans)}**.

⚠️ Без $ в C3 получилось бы `=B3/СУММ(B3:B102)` — неверно.''',
     fmt='NUMBER', files=[f], hint='Общая сумма должна быть закреплена знаками $.')
reference('xl03-4', lambda wb: (_helper(wb['Лист1'], 'C', 2, 101, lambda r: f'=B{r}/SUM($B$2:$B$101)'),
                                '=ROUND(MAX(Лист1!C2:C101)*100,2)')[1], f)

ans = sum(1 for a in range(1, 21) for b in range(1, 21) if a * b > 100)
task(L, 'xl03-5', 'Таблица умножения',
     'Построй таблицу умножения чисел от 1 до 20 (в A2:A21 — числа 1…20, в B1:U1 — числа 1…20, внутри — '
     'произведения). Сколько чисел в таблице **больше 100**?', ans,
     f'''1. A2:A21 и B1:U1 заполни числами 1…20 (автозаполнением).
2. В **B2**: `=$A2*B$1`. $A — столбец множителя закреплён, $1 — строка закреплена.
3. Протяни B2 вправо до U2, потом всю строку вниз до 21-й строки.
4. `=СЧЁТЕСЛИ(B2:U21;">100")` → **{ans}**.''',
     hint='Одна формула со смешанными ссылками: =$A2*B$1.')


def _ref305(wb):
    ws = wb.active
    for i in range(1, 21):
        ws.cell(i + 1, 1, i)
        ws.cell(1, i + 1, i)
    for r in range(2, 22):
        for c in range(2, 22):
            ws.cell(r, c, f'=$A{r}*{get_column_letter(c)}$1')
    return f"=COUNTIF('{ws.title}'!B2:U21,\">100\")"


reference('xl03-5', _ref305)

rnd = random.Random(306)
d306 = [[round(rnd.uniform(1, 300), 2)] for _ in range(150)]
rate = 92.37
wb306 = [('Лист1', ['Цена, $'], d306)]
f = 'xl03-6.xlsx'
book = write_book(FILES / f, wb306)
book['Лист1']['D1'] = 'Курс'
book['Лист1']['E1'] = rate
book.save(FILES / f)
ans = round2(sum(x * rate for (x,) in d306))
task(L, 'xl03-6', 'Цены в рублях',
     f'В файле — цены в долларах (столбец A), в ячейке **E1** — курс доллара. Посчитай цену каждого товара в рублях '
     f'в столбце B и найди сумму всех цен в рублях. Ответ округли до сотых.', fmt_num(ans),
     f'''1. В **B2**: `=A2*$E$1`. Курс один на всех, поэтому ссылка на E1 **абсолютная**.
2. Протяни до B151.
3. `=ОКРУГЛ(СУММ(B2:B151);2)` → **{fmt_num(ans)}**.''',
     fmt='NUMBER', files=[f])
reference('xl03-6', lambda wb: (_helper(wb['Лист1'], 'B', 2, 151, lambda r: f'=A{r}*$E$1'),
                                '=ROUND(SUM(Лист1!B2:B151),2)')[1], f)

copy_choice('xl03-7', 'Протянули вниз', '=B2*$C$1', 'D2', 'D7',
            ['=B7*$C$6', '=B2*$C$1', '=G2*$C$1'],
            'В ячейке **D2** записана формула `=B2*$C$1`. Её протянули вниз до **D7**.')

# ============================================================================ Урок 4
L = lesson('04-functions')

rnd = random.Random(401)
classes = ['10А', '10Б', '11А', '11Б']
students = [[person(rnd), rnd.choice(classes), rnd.randint(0, 100)] for _ in range(600)]
f_st = data_file('xl04-students.xlsx', [('Лист1', ['Ученик', 'Класс', 'Балл'], students)])
scores = [s[2] for s in students]
avg = sum(scores) / len(scores)
N = len(students) + 1
task(L, 'xl04-1', 'Средний балл',
     'В файле — ученики, их классы и баллы за тест. Найди **средний балл** всех учеников. Ответ округли до сотых.',
     fmt_num(round2(avg)),
     f'''`=СРЗНАЧ(C2:C{N})` → {avg:.4f}… Округляем: `=ОКРУГЛ(СРЗНАЧ(C2:C{N});2)` → **{fmt_num(round2(avg))}**.

СРЗНАЧ = сумма / количество чисел; пустые ячейки и текст не учитываются.''',
     fmt='NUMBER', files=[f_st])
reference('xl04-1', lambda wb: f'=ROUND(AVERAGE(Лист1!C2:C{N}),2)', f_st)

above = sum(1 for x in scores if x > avg)
task(L, 'xl04-2', 'Выше среднего',
     'Сколько учеников получили балл **строго выше** среднего?', above,
     f'''Условие в СЧЁТЕСЛИ можно склеить из знака и значения через `&`:

`=СЧЁТЕСЛИ(C2:C{N};">"&СРЗНАЧ(C2:C{N}))` → **{above}**.

Можно и в два шага: среднее в F1, потом `=СЧЁТЕСЛИ(C2:C{N};">"&F1)`.

⚠️ Запись `">СРЗНАЧ(C2:C{N})"` неверна — в кавычках формула превращается в текст.''',
     files=[f_st], hint='Условие: ">"&СРЗНАЧ(...)')
reference('xl04-2', lambda wb: f'=COUNTIF(Лист1!C2:C{N},">"&AVERAGE(Лист1!C2:C{N}))', f_st)

s10b = sum(s[2] for s in students if s[1] == '10Б')
task(L, 'xl04-3', 'Сумма по условию',
     'Найди **сумму баллов** всех учеников класса **10Б**.', s10b,
     f'''**СУММЕСЛИ**(где проверять; условие; что складывать):

`=СУММЕСЛИ(B2:B{N};"10Б";C2:C{N})` → **{s10b}**.''',
     files=[f_st])
reference('xl04-3', lambda wb: f'=SUMIF(Лист1!B2:B{N},"10Б",Лист1!C2:C{N})', f_st)

even = sum(1 for x in scores if x % 2 == 0)
task(L, 'xl04-4', 'Сколько чётных',
     'Сколько учеников получили **чётный** балл (0 тоже чётное)?', even,
     f'''Функции «чётное» в СЧЁТЕСЛИ нет, поэтому заводим **вспомогательный столбец**:

1. В **D2**: `=ОСТАТ(C2;2)` — остаток от деления на 2 (0 у чётных, 1 у нечётных). Протяни вниз.
2. `=СЧЁТЕСЛИ(D2:D{N};0)` → **{even}**.''',
     files=[f_st], hint='Вспомогательный столбец =ОСТАТ(C2;2).')
reference('xl04-4', lambda wb: (_helper(wb['Лист1'], 'D', 2, N, lambda r: f'=MOD(C{r},2)'),
                                f'=COUNTIF(Лист1!D2:D{N},0)')[1], f_st)

c11a = sum(1 for s in students if s[1] == '11А' and s[2] >= 70)
task(L, 'xl04-5', 'Несколько условий',
     'Сколько учеников **11А** класса получили **70 баллов и больше**?', c11a,
     f'''**СЧЁТЕСЛИМН** принимает пары «диапазон; условие» — все условия должны выполниться одновременно:

`=СЧЁТЕСЛИМН(B2:B{N};"11А";C2:C{N};">=70")` → **{c11a}**.''',
     files=[f_st])
reference('xl04-5', lambda wb: f'=COUNTIFS(Лист1!B2:B{N},"11А",Лист1!C2:C{N},">=70")', f_st)

rnd = random.Random(406)
shops = [f'М{i}' for i in range(1, 9)]
goods = ['Молоко', 'Хлеб', 'Сыр', 'Яблоки', 'Кефир', 'Чай']
sales = [[rnd.choice(shops), rnd.choice(goods), rnd.randint(100, 5000)] for _ in range(700)]
f = data_file('xl04-sales.xlsx', [('Лист1', ['Магазин', 'Товар', 'Сумма'], sales)])
ans = sum(s[2] for s in sales if s[0] == 'М3' and s[1] == 'Молоко')
task(L, 'xl04-6', 'Продажи товара в магазине',
     'В файле — продажи: магазин, товар, сумма. Найди общую сумму продаж товара **«Молоко»** в магазине **М3**.', ans,
     f'''**СУММЕСЛИМН**(что складывать; диапазон1; условие1; диапазон2; условие2):

`=СУММЕСЛИМН(C2:C701;A2:A701;"М3";B2:B701;"Молоко")` → **{ans}**.

⚠️ В СУММЕСЛИМН диапазон суммирования идёт **первым**, а в СУММЕСЛИ — последним.''',
     files=[f])
reference('xl04-6', lambda wb: '=SUMIFS(Лист1!C2:C701,Лист1!A2:A701,"М3",Лист1!B2:B701,"Молоко")', f)

avg11b = sum(s[2] for s in students if s[1] == '11Б') / sum(1 for s in students if s[1] == '11Б')
task(L, 'xl04-7', 'Среднее по условию',
     'Найди средний балл учеников **11Б** класса. Ответ округли до сотых.', fmt_num(round2(avg11b)),
     f'''`=ОКРУГЛ(СРЗНАЧЕСЛИ(B2:B{N};"11Б";C2:C{N});2)` → **{fmt_num(round2(avg11b))}**.''',
     fmt='NUMBER', files=[f_st])
reference('xl04-7', lambda wb: f'=ROUND(AVERAGEIF(Лист1!B2:B{N},"11Б",Лист1!C2:C{N}),2)', f_st)

rnd = random.Random(408)
meas = [[rnd.randint(0, 999) / 10] for _ in range(400)]
f = data_file('xl04-measures.xlsx', [('Лист1', ['Измерение'], meas)])
ans = sum(round_half_up(x) for (x,) in meas)
task(L, 'xl04-8', 'Сумма округлённых',
     'В файле — результаты измерений с одной цифрой после запятой. Округли **каждое** значение до целого '
     '(по правилам математики) и найди сумму округлённых значений.', ans,
     f'''1. В **B2**: `=ОКРУГЛ(A2;0)` — второй аргумент 0 значит «до целых». 2,5 → 3, 2,4 → 2. Протяни вниз.
2. `=СУММ(B2:B401)` → **{ans}**.

⚠️ Кнопка «Уменьшить разрядность» на ленте только **прячет** дробную часть, а в ячейке остаётся 2,5 — для подсчёта
она не годится. Сумма неокруглённых значений была бы {sum(x for (x,) in meas):.1f}.''',
     files=[f], hint='=ОКРУГЛ(A2;0) во вспомогательном столбце.')
reference('xl04-8', lambda wb: (_helper(wb['Лист1'], 'B', 2, 401, lambda r: f'=ROUND(A{r},0)'), '=SUM(Лист1!B2:B401)')[1], f)

# ============================================================================ Урок 5
L = lesson('05-if')

rnd = random.Random(501)
exam = [[person(rnd), rnd.randint(10, 100), rnd.randint(10, 100), rnd.randint(10, 100)] for _ in range(800)]
f_ex = data_file('xl05-exam.xlsx', [('Лист1', ['Ученик', 'Математика', 'Русский', 'Информатика'], exam)])
NE = len(exam) + 1


def cnt(pred):
    return sum(1 for r in exam if pred(*r[1:]))


ans = cnt(lambda m, r, i: m >= 27 and r >= 24 and i >= 40)
task(L, 'xl05-1', 'Сдал все экзамены',
     'Пороги: математика — 27, русский — 24, информатика — 40 баллов. Ученик сдал, если набрал **не меньше порога '
     'по каждому** предмету. Сколько учеников сдали все три экзамена?', ans,
     f'''1. В **E2**: `=ЕСЛИ(И(B2>=27;C2>=24;D2>=40);1;0)` — 1, если все три условия выполнены. Протяни вниз.
2. `=СУММ(E2:E{NE})` → **{ans}**.

Приём «столбец из 1 и 0 + СУММ» работает для любых условий.''',
     files=[f_ex], hint='И(...) истинно, только если истинны все условия.')
reference('xl05-1', lambda wb: (_helper(wb['Лист1'], 'E', 2, NE, lambda r: f'=IF(AND(B{r}>=27,C{r}>=24,D{r}>=40),1,0)'),
                                f'=SUM(Лист1!E2:E{NE})')[1], f_ex)

ans = cnt(lambda m, r, i: m >= 90 or r >= 90 or i >= 90)
task(L, 'xl05-2', 'Хотя бы один высокий балл',
     'Сколько учеников набрали **90 и больше хотя бы по одному** предмету?', ans,
     f'''**ИЛИ** истинно, если выполнено хотя бы одно условие:

`=ЕСЛИ(ИЛИ(B2>=90;C2>=90;D2>=90);1;0)` в E2, протянуть, `=СУММ(E2:E{NE})` → **{ans}**.''',
     files=[f_ex])
reference('xl05-2', lambda wb: (_helper(wb['Лист1'], 'E', 2, NE, lambda r: f'=IF(OR(B{r}>=90,C{r}>=90,D{r}>=90),1,0)'),
                                f'=SUM(Лист1!E2:E{NE})')[1], f_ex)


def grade(i):
    return 5 if i >= 85 else 4 if i >= 65 else 3 if i >= 40 else 2


ans = cnt(lambda m, r, i: grade(i) == 4)
task(L, 'xl05-3', 'Оценка по баллам',
     'Оценка по информатике: 85 и больше — «5», от 65 — «4», от 40 — «3», меньше 40 — «2». '
     'Сколько учеников получили **«4»**?', ans,
     f'''Вложенные ЕСЛИ проверяют пороги по очереди, от большего к меньшему:

`=ЕСЛИ(D2>=85;5;ЕСЛИ(D2>=65;4;ЕСЛИ(D2>=40;3;2)))` в E2, протянуть.

`=СЧЁТЕСЛИ(E2:E{NE};4)` → **{ans}**.

Почему порядок важен: если первым проверить D2>=40, то и 90 баллов получат «3».''',
     files=[f_ex], hint='Проверяй пороги от большего к меньшему.')
reference('xl05-3', lambda wb: (_helper(wb['Лист1'], 'E', 2, NE, lambda r: f'=IF(D{r}>=85,5,IF(D{r}>=65,4,IF(D{r}>=40,3,2)))'),
                                f'=COUNTIF(Лист1!E2:E{NE},4)')[1], f_ex)

ans = cnt(lambda m, r, i: (m >= 60) + (r >= 60) + (i >= 60) == 2)
task(L, 'xl05-4', 'Ровно два предмета',
     'Сколько учеников набрали **60 и больше ровно по двум** предметам из трёх?', ans,
     f'''«Ровно два» удобно считать **сложением** условий: в Excel ИСТИНА при сложении превращается в 1.

1. В **E2**: `=(B2>=60)+(C2>=60)+(D2>=60)` — сколько предметов сданы на 60+.
2. `=СЧЁТЕСЛИ(E2:E{NE};2)` → **{ans}**.''',
     files=[f_ex], hint='Сложи три сравнения: (B2>=60)+(C2>=60)+(D2>=60).')
reference('xl05-4', lambda wb: (_helper(wb['Лист1'], 'E', 2, NE, lambda r: f'=(B{r}>=60)+(C{r}>=60)+(D{r}>=60)'),
                                f'=COUNTIF(Лист1!E2:E{NE},2)')[1], f_ex)

ans = cnt(lambda m, r, i: not (i > m))
task(L, 'xl05-5', 'Функция НЕ',
     'Сколько учеников, у которых балл по информатике **не больше**, чем по математике?', ans,
     f'''`=ЕСЛИ(НЕ(D2>B2);1;0)` — то же, что `=ЕСЛИ(D2<=B2;1;0)`. Протянуть, `=СУММ(E2:E{NE})` → **{ans}**.''',
     files=[f_ex])
reference('xl05-5', lambda wb: (_helper(wb['Лист1'], 'E', 2, NE, lambda r: f'=IF(NOT(D{r}>B{r}),1,0)'),
                                f'=SUM(Лист1!E2:E{NE})')[1], f_ex)

rnd = random.Random(506)
mgr = [[person(rnd), rnd.randint(20_000, 200_000)] for _ in range(120)]
f = data_file('xl05-sales.xlsx', [('Лист1', ['Менеджер', 'Продажи'], mgr)])
ans = round2(sum(x * 0.05 for _, x in mgr if x > 100_000))
task(L, 'xl05-6', 'Премия менеджерам',
     'Менеджер получает премию 5 % от продаж, если продал **больше 100 000** рублей, иначе премии нет. '
     'Какова общая сумма премий? Ответ округли до сотых.', fmt_num(ans),
     f'''1. В **C2**: `=ЕСЛИ(B2>100000;B2*0,05;0)`. Протяни до C121.
2. `=ОКРУГЛ(СУММ(C2:C121);2)` → **{fmt_num(ans)}**.''',
     fmt='NUMBER', files=[f])
reference('xl05-6', lambda wb: (_helper(wb['Лист1'], 'C', 2, 121, lambda r: f'=IF(B{r}>100000,B{r}*0.05,0)'),
                                '=ROUND(SUM(Лист1!C2:C121),2)')[1], f)

task(L, 'xl05-7', 'Что вернёт ЕСЛИ',
     'В A1 записано 5, в B1 — минус 1. Что покажет формула `=ЕСЛИ(И(A1>0;B1>0);1;0)`?', 2,
     '''A1 > 0 — истина, B1 > 0 — ложь. **И** истинно, только если истинны оба условия, значит И(...) = ЛОЖЬ,
и ЕСЛИ выбирает третий аргумент: **0**.''',
     type_='CHOICE', options=['1', '0', 'ИСТИНА', '#ЗНАЧ!'])


def _ref507(wb):
    ws = wb.active
    ws['A1'] = 5
    ws['B1'] = -1
    return f"=IF(AND('{ws.title}'!A1>0,'{ws.title}'!B1>0),1,0)"


reference('xl05-7', _ref507, check='option')

# ============================================================================ Урок 6
L = lesson('06-sort-filter')

rnd = random.Random(601)
cities = ['Казань', 'Самара', 'Пермь', 'Уфа', 'Омск', 'Тула']
managers = ['Орлов', 'Ильина', 'Ким', 'Зуева', 'Белов', 'Петрова', 'Гусев']
sums = rnd.sample(range(1000, 20000), 400)
orders = []
for i in range(400):
    orders.append([i + 1, rnd.choice(cities), rnd.choice(managers), rnd.choice(SURNAMES), sums[i], rnd.randint(1, 60)])
f_or = data_file('xl06-orders.xlsx', [('Лист1', ['ID', 'Город', 'Менеджер', 'Клиент', 'Сумма', 'Количество'], orders)])
NO = len(orders) + 1
s_sorted = sorted((o[4] for o in orders), reverse=True)
task(L, 'xl06-1', 'Пятая по величине сумма',
     'В файле — заказы. Отсортируй таблицу по убыванию суммы. Какая сумма окажется **пятой** сверху?', s_sorted[4],
     f'''1. Встань в любую ячейку таблицы → **Данные → Сортировка** → «Сумма», «По убыванию». Excel сам выделит всю таблицу.
2. Пятая строка данных (строка 6 листа) — сумма **{s_sorted[4]}**.

Без сортировки: `=НАИБОЛЬШИЙ(E2:E{NO};5)` — пятое по величине значение.''',
     files=[f_or])
reference('xl06-1', lambda wb: f'=LARGE(Лист1!E2:E{NO},5)', f_or)

ans = sum(1 for o in orders if o[1] == 'Казань' and o[4] > 5000)
task(L, 'xl06-2', 'Фильтр по двум условиям',
     'С помощью фильтра найди, сколько заказов из **Казани** на сумму **больше 5000**.', ans,
     f'''1. **Данные → Фильтр**. В заголовках появятся кнопки ▾.
2. «Город» ▾ → оставь только «Казань».
3. «Сумма» ▾ → Числовые фильтры → **больше** → 5000.
4. Выдели столбец ID — в строке состояния «Количество: {ans + 1}» (вместе с заголовком). Заказов: **{ans}**.

Формулой: `=СЧЁТЕСЛИМН(B2:B{NO};"Казань";E2:E{NO};">5000")`.''',
     files=[f_or])
reference('xl06-2', lambda wb: f'=COUNTIFS(Лист1!B2:B{NO},"Казань",Лист1!E2:E{NO},">5000")', f_or)

ans = len({o[3] for o in orders})
task(L, 'xl06-3', 'Различные клиенты',
     'Сколько **различных** фамилий клиентов встречается в таблице?', ans,
     f'''Способ 1 (Excel 365): `=СЧЁТЗ(УНИК(D2:D{NO}))`.

Способ 2 (везде): скопируй столбец D на новый лист → **Данные → Удалить дубликаты** → посчитай оставшиеся строки.
В LibreOffice: **Данные → Ещё фильтры → Стандартный фильтр**, условие «Клиент» = «не пусто», в «Параметрах» — «Без повторений».

Способ 3 (формула для всех версий): `=СУММПРОИЗВ(1/СЧЁТЕСЛИ(D2:D{NO};D2:D{NO}))` — каждая фамилия даёт в сумме ровно 1.

Ответ: **{ans}**.''',
     files=[f_or], hint='Удали дубликаты в копии столбца и посчитай, сколько осталось.')
reference('xl06-3', lambda wb: f'=SUMPRODUCT(1/COUNTIF(Лист1!D2:D{NO},Лист1!D2:D{NO}))', f_or)

third = sorted(orders, key=lambda o: -o[4])[2][0]
task(L, 'xl06-4', 'Кто третий после сортировки',
     'Отсортируй заказы по **убыванию суммы**. Какой **ID** у заказа, который окажется третьим?', third,
     f'''После сортировки по «Сумме» по убыванию третья строка данных — заказ с ID **{third}**.

Формулой без сортировки: `=ИНДЕКС(A2:A{NO};ПОИСКПОЗ(НАИБОЛЬШИЙ(E2:E{NO};3);E2:E{NO};0))`.

⚠️ Если перед сортировкой выделить только столбец «Сумма», ID останутся на месте и ответ будет неверным.''',
     files=[f_or])
reference('xl06-4', lambda wb: f'=INDEX(Лист1!A2:A{NO},MATCH(LARGE(Лист1!E2:E{NO},3),Лист1!E2:E{NO},0))', f_or)

qty = [o[5] for o in orders]
ans = sum(1 for q in qty if qty.count(q) > 1)
task(L, 'xl06-5', 'Подсветка повторов',
     'Сколько ячеек в столбце «Количество» содержат значение, которое встречается в этом столбце **больше одного раза**?', ans,
     f'''1. Выдели F2:F{NO} → **Главная → Условное форматирование → Правила выделения ячеек → Повторяющиеся значения**.
   Все ячейки-повторы станут красными.
2. Посчитать красные удобно фильтром по цвету: «Количество» ▾ → Фильтр по цвету.

Формулой: вспомогательный столбец `=СЧЁТЕСЛИ($F$2:$F${NO};F2)>1`, затем `=СЧЁТЕСЛИ(G2:G{NO};ИСТИНА)` → **{ans}**.''',
     files=[f_or])
reference('xl06-5', lambda wb: (_helper(wb['Лист1'], 'G', 2, NO, lambda r: f'=IF(COUNTIF($F$2:$F${NO},F{r})>1,1,0)'),
                                f'=SUM(Лист1!G2:G{NO})')[1], f_or)

ans = sum(o[4] for o in orders if o[2] == 'Ким' and o[1] == 'Пермь')
task(L, 'xl06-6', 'Сумма отфильтрованного',
     'Отфильтруй заказы менеджера **Ким** из города **Пермь**. Какова их общая сумма?', ans,
     f'''Фильтр: «Менеджер» = Ким, «Город» = Пермь. Выдели видимые ячейки столбца «Сумма» — строка состояния покажет
**Сумма: {ans}**.

⚠️ Обычная `=СУММ(E:E)` сложит и скрытые фильтром строки. Нужна **ПРОМЕЖУТОЧНЫЕ.ИТОГИ(9;E2:E{NO})** или
`=СУММЕСЛИМН(E2:E{NO};C2:C{NO};"Ким";B2:B{NO};"Пермь")`.''',
     files=[f_or])
reference('xl06-6', lambda wb: f'=SUMIFS(Лист1!E2:E{NO},Лист1!C2:C{NO},"Ким",Лист1!B2:B{NO},"Пермь")', f_or)

ans = sum(1 for o in orders if o[3].startswith('Ан'))
task(L, 'xl06-7', 'Текстовый фильтр',
     'Сколько заказов от клиентов, чья фамилия **начинается на «Ан»**?', ans,
     f'''Фильтр «Клиент» ▾ → Текстовые фильтры → **начинается с** → «Ан».

Формулой: `=СЧЁТЕСЛИ(D2:D{NO};"Ан*")` — звёздочка значит «любые символы дальше». Ответ: **{ans}**.''',
     files=[f_or])
reference('xl06-7', lambda wb: f'=COUNTIF(Лист1!D2:D{NO},"Ан*")', f_or)

# ============================================================================ Урок 7 (задание 9)
L = lesson('07-task9')
ROWS9 = 5000


def task9(id_, title, seed, width, lo, hi, cond_text, check, helper_cols, answer_formula, solution_steps, hint=''):
    rnd_ = random.Random(seed)
    rows = [[rnd_.randint(lo, hi) for _ in range(width)] for _ in range(ROWS9)]
    name = f'{id_}.xlsx'
    write_book(FILES / name, [('Лист1', None, rows)])
    ans = sum(1 for r in rows if check(r))
    assert 0 < ans < ROWS9, (id_, ans)
    last = get_column_letter(width)
    task(L, id_, title,
         f'''Откройте файл электронной таблицы. В каждой строке записаны {width} натуральных чисел (столбцы A:{last}).
Определите количество строк таблицы, для которых выполнены **оба** условия:

{cond_text}

В ответе запишите только число.''', ans,
         solution_steps(width, ans), files=[name], hint=hint)

    def build(wb):
        ws = wb['Лист1']
        for col, f_ in helper_cols:
            _helper(ws, col, 1, ROWS9, f_)
        return answer_formula
    reference(id_, build, name)
    return rows


def counts_ok(r, pattern):
    return sorted(r.count(x) for x in r) == pattern


def t9_1(r):
    if not counts_ok(r, [1, 1, 1, 1, 2, 2]):
        return False
    rep = [x for x in r if r.count(x) == 2][0]
    rest = [x for x in r if r.count(x) == 1]
    return sum(rest) / len(rest) <= rep


task9('xl07-1', 'Задание 9: один повтор', 701, 6, 1, 30,
      '- в строке **только одно** число повторяется ровно дважды, остальные четыре числа различны;\n'
      '- среднее арифметическое неповторяющихся чисел строки **не больше** повторяющегося числа.',
      t9_1,
      [('G', lambda r: f'=COUNTIF($A{r}:$F{r},A{r})'), ('H', lambda r: f'=COUNTIF($A{r}:$F{r},B{r})'),
       ('I', lambda r: f'=COUNTIF($A{r}:$F{r},C{r})'), ('J', lambda r: f'=COUNTIF($A{r}:$F{r},D{r})'),
       ('K', lambda r: f'=COUNTIF($A{r}:$F{r},E{r})'), ('L', lambda r: f'=COUNTIF($A{r}:$F{r},F{r})'),
       ('M', lambda r: f'=IF(AND(COUNTIF(G{r}:L{r},2)=2,COUNTIF(G{r}:L{r},1)=4),1,0)'),
       ('N', lambda r: f'=IF(M{r}=1,AVERAGEIF(G{r}:L{r},1,A{r}:F{r})<=SUMIF(G{r}:L{r},2,A{r}:F{r})/2,FALSE)'),
       ('O', lambda r: f'=IF(AND(M{r}=1,N{r}),1,0)')],
      f'=SUM(Лист1!O1:O{ROWS9})',
      lambda w, a: f'''Каждое условие — в свой вспомогательный столбец, итог — СУММ.

1. **G:L — сколько раз встречается каждое число строки.** В G1: `=СЧЁТЕСЛИ($A1:$F1;A1)`, протяни вправо до L1.
   У повторяющегося числа будет 2, у остальных 1.
2. **M — первое условие.** Ровно одно число повторяется дважды ⇔ среди G:L ровно две двойки и четыре единицы:
   `=ЕСЛИ(И(СЧЁТЕСЛИ(G1:L1;2)=2;СЧЁТЕСЛИ(G1:L1;1)=4);1;0)`.
3. **N — второе условие.** Среднее неповторяющихся: `СРЗНАЧЕСЛИ(G1:L1;1;A1:F1)` (усредняет A:F там, где в G:L стоит 1).
   Повторяющееся число: `СУММЕСЛИ(G1:L1;2;A1:F1)/2` (оно сложилось дважды).
   `=ЕСЛИ(M1=1;СРЗНАЧЕСЛИ(G1:L1;1;A1:F1)<=СУММЕСЛИ(G1:L1;2;A1:F1)/2;ЛОЖЬ)`.
4. **O — оба условия:** `=ЕСЛИ(И(M1=1;N1);1;0)`.
5. Выдели G1:O1 и дважды щёлкни по маркеру — формулы протянутся на все {ROWS9} строк. Ответ: `=СУММ(O:O)` → **{a}**.''',
      hint='Начни со столбцов G:L: =СЧЁТЕСЛИ($A1:$F1;A1).')


def t9_2(r):
    a, b, c = sorted(r)
    return c < a + b and len(set(r)) < 3


task9('xl07-2', 'Задание 9: равнобедренный треугольник', 702, 3, 1, 60,
      '- из чисел строки можно составить **невырожденный треугольник** (наибольшее число меньше суммы двух других);\n'
      '- треугольник **равнобедренный**: в строке есть хотя бы два одинаковых числа.',
      t9_2,
      [('D', lambda r: f'=IF(MAX(A{r}:C{r})<SUM(A{r}:C{r})-MAX(A{r}:C{r}),1,0)'),
       ('E', lambda r: f'=IF(OR(A{r}=B{r},B{r}=C{r},A{r}=C{r}),1,0)'),
       ('F', lambda r: f'=D{r}*E{r}')],
      f'=SUM(Лист1!F1:F{ROWS9})',
      lambda w, a: f'''1. **D — треугольник.** Наибольшая сторона меньше суммы двух других: `=ЕСЛИ(МАКС(A1:C1)<СУММ(A1:C1)-МАКС(A1:C1);1;0)`.
2. **E — равнобедренный.** `=ЕСЛИ(ИЛИ(A1=B1;B1=C1;A1=C1);1;0)`.
3. **F — оба условия:** `=D1*E1` (1 · 1 = 1, иначе 0).
4. Протяни D1:F1 на все строки, `=СУММ(F:F)` → **{a}**.''')


def t9_3(r):
    return len(set(r)) == len(r) and 2 * (max(r) + min(r)) <= sum(r) - max(r) - min(r)


task9('xl07-3', 'Задание 9: все различны', 703, 7, 1, 120,
      '- все числа в строке **различны**;\n'
      '- удвоенная сумма максимального и минимального чисел строки **не больше** суммы оставшихся пяти чисел.',
      t9_3,
      [('H', lambda r: f'=IF(SUMPRODUCT(1/COUNTIF(A{r}:G{r},A{r}:G{r}))=7,1,0)'),
       ('I', lambda r: f'=IF(2*(MAX(A{r}:G{r})+MIN(A{r}:G{r}))<=SUM(A{r}:G{r})-MAX(A{r}:G{r})-MIN(A{r}:G{r}),1,0)'),
       ('J', lambda r: f'=H{r}*I{r}')],
      f'=SUM(Лист1!J1:J{ROWS9})',
      lambda w, a: f'''1. **H — все различны.** Количество различных чисел: `СУММПРОИЗВ(1/СЧЁТЕСЛИ(A1:G1;A1:G1))`. Если оно равно 7 — все различны:
   `=ЕСЛИ(СУММПРОИЗВ(1/СЧЁТЕСЛИ(A1:G1;A1:G1))=7;1;0)`.
   Попроще для новичка: семь столбцов `=СЧЁТЕСЛИ($A1:$G1;A1)` и проверка `=МАКС(…)=1`.
2. **I — второе условие.** Сумма оставшихся = СУММ − МАКС − МИН:
   `=ЕСЛИ(2*(МАКС(A1:G1)+МИН(A1:G1))<=СУММ(A1:G1)-МАКС(A1:G1)-МИН(A1:G1);1;0)`.
3. **J:** `=H1*I1`, протянуть на все строки, `=СУММ(J:J)` → **{a}**.''')


def t9_4(r):
    if not counts_ok(r, [1, 1, 1, 3, 3, 3]):
        return False
    rep = [x for x in r if r.count(x) == 3]
    rest = [x for x in r if r.count(x) == 1]
    return sum(rep) > sum(rest)


task9('xl07-4', 'Задание 9: число трижды', 704, 6, 1, 12,
      '- в строке есть число, которое повторяется **ровно три раза**, а остальные три числа различны;\n'
      '- сумма повторяющихся чисел (с учётом повторов) **больше** суммы остальных трёх.',
      t9_4,
      [('G', lambda r: f'=COUNTIF($A{r}:$F{r},A{r})'), ('H', lambda r: f'=COUNTIF($A{r}:$F{r},B{r})'),
       ('I', lambda r: f'=COUNTIF($A{r}:$F{r},C{r})'), ('J', lambda r: f'=COUNTIF($A{r}:$F{r},D{r})'),
       ('K', lambda r: f'=COUNTIF($A{r}:$F{r},E{r})'), ('L', lambda r: f'=COUNTIF($A{r}:$F{r},F{r})'),
       ('M', lambda r: f'=IF(AND(COUNTIF(G{r}:L{r},3)=3,COUNTIF(G{r}:L{r},1)=3),1,0)'),
       ('N', lambda r: f'=IF(SUMIF(G{r}:L{r},3,A{r}:F{r})>SUMIF(G{r}:L{r},1,A{r}:F{r}),1,0)'),
       ('O', lambda r: f'=M{r}*N{r}')],
      f'=SUM(Лист1!O1:O{ROWS9})',
      lambda w, a: f'''1. **G:L:** `=СЧЁТЕСЛИ($A1:$F1;A1)` — сколько раз встречается каждое число.
2. **M — первое условие:** ровно три тройки и три единицы: `=ЕСЛИ(И(СЧЁТЕСЛИ(G1:L1;3)=3;СЧЁТЕСЛИ(G1:L1;1)=3);1;0)`.
3. **N — второе:** `=ЕСЛИ(СУММЕСЛИ(G1:L1;3;A1:F1)>СУММЕСЛИ(G1:L1;1;A1:F1);1;0)`.
4. **O:** `=M1*N1`, протянуть, `=СУММ(O:O)` → **{a}**.''')


def t9_5(r):
    mx, mn = max(r), min(r)
    rest = sorted(r)[1:-1]
    return r.count(mx) == 1 and (mx + mn) ** 2 > sum(x * x for x in rest)


task9('xl07-5', 'Задание 9: суммы квадратов', 705, 5, 1, 100,
      '- наибольшее число строки встречается в ней **только один раз**;\n'
      '- квадрат суммы наибольшего и наименьшего чисел **больше** суммы квадратов трёх оставшихся чисел.',
      t9_5,
      [('F', lambda r: f'=IF(COUNTIF(A{r}:E{r},MAX(A{r}:E{r}))=1,1,0)'),
       ('G', lambda r: f'=IF((MAX(A{r}:E{r})+MIN(A{r}:E{r}))^2>SUMSQ(A{r}:E{r})-MAX(A{r}:E{r})^2-MIN(A{r}:E{r})^2,1,0)'),
       ('H', lambda r: f'=F{r}*G{r}')],
      f'=SUM(Лист1!H1:H{ROWS9})',
      lambda w, a: f'''1. **F — максимум единственный:** `=ЕСЛИ(СЧЁТЕСЛИ(A1:E1;МАКС(A1:E1))=1;1;0)`.
2. **G — второе условие.** Сумма квадратов всех чисел — **СУММКВ**; вычитаем квадраты максимума и минимума:
   `=ЕСЛИ((МАКС(A1:E1)+МИН(A1:E1))^2>СУММКВ(A1:E1)-МАКС(A1:E1)^2-МИН(A1:E1)^2;1;0)`.
3. **H:** `=F1*G1`, протянуть, `=СУММ(H:H)` → **{a}**.

Замечание: если минимум повторяется, «оставшиеся три» всё равно три числа — мы вычли ровно одно значение минимума.''')


def t9_6(r):
    a, b, c, d = r
    return len(set(r)) == 4 and (a + b == c + d or a + c == b + d or a + d == b + c)


task9('xl07-6', 'Задание 9: две пары', 706, 4, 1, 40,
      '- все четыре числа строки **различны**;\n'
      '- числа можно разбить на **две пары с равными суммами**.',
      t9_6,
      [('E', lambda r: f'=IF(SUMPRODUCT(1/COUNTIF(A{r}:D{r},A{r}:D{r}))=4,1,0)'),
       ('F', lambda r: f'=IF(OR(A{r}+B{r}=C{r}+D{r},A{r}+C{r}=B{r}+D{r},A{r}+D{r}=B{r}+C{r}),1,0)'),
       ('G', lambda r: f'=E{r}*F{r}')],
      f'=SUM(Лист1!G1:G{ROWS9})',
      lambda w, a: f'''1. **E — различны:** `=ЕСЛИ(СУММПРОИЗВ(1/СЧЁТЕСЛИ(A1:D1;A1:D1))=4;1;0)`.
2. **F — пары.** Из четырёх чисел пары можно составить тремя способами — проверяем все:
   `=ЕСЛИ(ИЛИ(A1+B1=C1+D1;A1+C1=B1+D1;A1+D1=B1+C1);1;0)`.
3. **G:** `=E1*F1`, протянуть, `=СУММ(G:G)` → **{a}**.''')

# ============================================================================ Урок 8 (задание 3)
L = lesson('08-task3')

rnd = random.Random(801)
districts = ['Центральный', 'Заречный', 'Октябрьский', 'Северный', 'Промышленный']
shops_db = []
for i in range(1, 19):
    shops_db.append([f'M{i}', districts[(i - 1) % len(districts)], f'ул. {rnd.choice(["Лесная", "Садовая", "Мира", "Новая", "Школьная"])}, {rnd.randint(1, 90)}'])
products = [
    (1, 'Молочная продукция', 'Молоко 3,2%', 'л', 1, 'Молокозавод'), (2, 'Молочная продукция', 'Кефир 2,5%', 'л', 1, 'Молокозавод'),
    (3, 'Молочная продукция', 'Сметана 20%', 'кг', 0.5, 'Молокозавод'), (4, 'Бакалея', 'Сахар песок', 'кг', 2, 'Сахарный комбинат'),
    (5, 'Бакалея', 'Гречка', 'кг', 1, 'Агрохолдинг'), (6, 'Бакалея', 'Рис круглый', 'кг', 1, 'Агрохолдинг'),
    (7, 'Хлеб', 'Хлеб белый', 'шт', 1, 'Хлебозавод'), (8, 'Хлеб', 'Батон нарезной', 'шт', 1, 'Хлебозавод'),
    (9, 'Яйца', 'Яйцо диетическое', 'шт', 10, 'Птицефабрика'), (10, 'Яйца', 'Яйцо столовое', 'шт', 10, 'Птицефабрика'),
    (11, 'Бакалея', 'Соль поваренная', 'кг', 1, 'Солепромысел'), (12, 'Молочная продукция', 'Творог 9%', 'кг', 0.5, 'Молокозавод')]
price = {p[0]: rnd.randint(40, 220) for p in products}
moves = []
start = date(2021, 6, 1)
for op in range(1, 3201):
    shop = rnd.choice(shops_db)[0]
    art = rnd.choice(products)[0]
    kind = 'Поступление' if rnd.random() < 0.55 else 'Продажа'
    qty = rnd.randint(20, 180) if kind == 'Поступление' else rnd.randint(5, 120)
    moves.append([op, start + timedelta(days=rnd.randint(0, 29)), shop, art, kind, qty, price[art]])
moves.sort(key=lambda m: (m[1], m[0]))
for i, m in enumerate(moves):
    m[0] = i + 1
f_db = data_file('xl08-db.xlsx', [
    ('Движение товаров', ['ID операции', 'Дата', 'ID магазина', 'Артикул', 'Тип операции', 'Количество упаковок, шт', 'Цена, руб./шт'], moves),
    ('Товар', ['Артикул', 'Отдел', 'Наименование товара', 'Ед. изм.', 'Количество в упаковке', 'Поставщик'], [list(p) for p in products]),
    ('Магазин', ['ID магазина', 'Район', 'Адрес'], shops_db)])
NM = len(moves) + 1
district_of = {s[0]: s[1] for s in shops_db}
prod = {p[0]: p for p in products}
DB_INTRO = ('В файле приведён фрагмент базы данных «Продукты» о поставках товаров в магазины районов города. '
            'База данных состоит из трёх таблиц (листов): «Движение товаров» (операции за июнь 2021 года), '
            '«Товар» и «Магазин».\n\n![Связи таблиц](images/xl-db-schema.svg)\n\n')
MV = "'Движение товаров'"


def db_helpers(wb):
    """Вспомогательные столбцы на листе «Движение товаров» — так делается в разборе: район, наименование, отдел, в упаковке."""
    ws = wb['Движение товаров']
    ws['H1'], ws['I1'], ws['J1'], ws['K1'] = 'Район', 'Наименование', 'Отдел', 'В упаковке'
    _helper(ws, 'H', 2, NM, lambda r: f'=VLOOKUP(C{r},Магазин!$A$2:$C$19,2,FALSE)')
    _helper(ws, 'I', 2, NM, lambda r: f'=VLOOKUP(D{r},Товар!$A$2:$F$13,3,FALSE)')
    _helper(ws, 'J', 2, NM, lambda r: f'=VLOOKUP(D{r},Товар!$A$2:$F$13,2,FALSE)')
    _helper(ws, 'K', 2, NM, lambda r: f'=VLOOKUP(D{r},Товар!$A$2:$F$13,5,FALSE)')


def mv(pred):
    return [m for m in moves if pred(m)]


HELP_TEXT = '''Сначала «подтягиваем» нужные поля в лист «Движение товаров» функцией **ВПР** (вспомогательные столбцы справа):

- H «Район»: `=ВПР(C2;Магазин!$A$2:$C$19;2;ЛОЖЬ)`
- I «Наименование»: `=ВПР(D2;Товар!$A$2:$F$13;3;ЛОЖЬ)`
- J «Отдел»: `=ВПР(D2;Товар!$A$2:$F$13;2;ЛОЖЬ)`
- K «В упаковке»: `=ВПР(D2;Товар!$A$2:$F$13;5;ЛОЖЬ)`

Протягиваем их на все строки. Дальше — фильтр или СУММЕСЛИМН.'''

ans = sum(m[5] for m in mv(lambda m: prod[m[3]][2] == 'Яйцо диетическое' and district_of[m[2]] == 'Заречный'
                           and m[4] == 'Поступление' and m[1] <= date(2021, 6, 10)))
task(L, 'xl08-1', 'Поступление товара в район',
     DB_INTRO + 'Сколько упаковок товара **«Яйцо диетическое»** поступило в магазины **Заречного** района '
     'за период с 1 по 10 июня включительно?', ans,
     HELP_TEXT + f'''

Ответ формулой:
`=СУММЕСЛИМН(F2:F{NM};I2:I{NM};"Яйцо диетическое";H2:H{NM};"Заречный";E2:E{NM};"Поступление";B2:B{NM};"<="&ДАТА(2021;6;10))`
→ **{ans}**.

Фильтром: «Наименование» = Яйцо диетическое, «Район» = Заречный, «Тип операции» = Поступление, «Дата» ≤ 10.06.2021,
затем сумма столбца F в строке состояния.''',
     files=[f_db], hint='Подтяни район и наименование в основную таблицу через ВПР.')
reference('xl08-1', lambda wb: (db_helpers(wb), f'=SUMIFS({MV}!F2:F{NM},{MV}!I2:I{NM},"Яйцо диетическое",{MV}!H2:H{NM},"Заречный",'
                                               f'{MV}!E2:E{NM},"Поступление",{MV}!B2:B{NM},"<="&DATE(2021,6,10))')[1], f_db)

inc = sum(m[5] for m in mv(lambda m: prod[m[3]][2] == 'Молоко 3,2%' and district_of[m[2]] == 'Октябрьский' and m[4] == 'Поступление'))
dec = sum(m[5] for m in mv(lambda m: prod[m[3]][2] == 'Молоко 3,2%' and district_of[m[2]] == 'Октябрьский' and m[4] == 'Продажа'))
task(L, 'xl08-2', 'На сколько увеличилось количество',
     DB_INTRO + 'На сколько **увеличилось** количество упаковок товара **«Молоко 3,2%»** в магазинах **Октябрьского** '
     'района за весь июнь (поступило минус продано)?', inc - dec,
     HELP_TEXT + f'''

1. Поступило: `=СУММЕСЛИМН(F2:F{NM};I2:I{NM};"Молоко 3,2%";H2:H{NM};"Октябрьский";E2:E{NM};"Поступление")` = {inc}.
2. Продано: то же с «Продажа» = {dec}.
3. Увеличение: {inc} − {dec} = **{inc - dec}**.''',
     files=[f_db])
reference('xl08-2', lambda wb: (db_helpers(wb),
                                f'=SUMIFS({MV}!F2:F{NM},{MV}!I2:I{NM},"Молоко 3,2%",{MV}!H2:H{NM},"Октябрьский",{MV}!E2:E{NM},"Поступление")'
                                f'-SUMIFS({MV}!F2:F{NM},{MV}!I2:I{NM},"Молоко 3,2%",{MV}!H2:H{NM},"Октябрьский",{MV}!E2:E{NM},"Продажа")')[1], f_db)

kg = sum(m[5] * prod[m[3]][4] for m in mv(lambda m: prod[m[3]][2] == 'Сахар песок' and district_of[m[2]] == 'Центральный' and m[4] == 'Продажа'))
assert kg == int(kg)
task(L, 'xl08-3', 'Килограммы, а не упаковки',
     DB_INTRO + 'Сколько **килограммов** товара **«Сахар песок»** продано в магазинах **Центрального** района за июнь?',
     int(kg),
     HELP_TEXT + f'''

В одной упаковке сахара — {prod[4][4]} кг (столбец «Количество в упаковке» листа «Товар»). Поэтому нужен ещё столбец
L «Килограммы»: `=F2*K2`.

`=СУММЕСЛИМН(L2:L{NM};I2:I{NM};"Сахар песок";H2:H{NM};"Центральный";E2:E{NM};"Продажа")` → **{int(kg)}**.

⚠️ Частая ошибка — ответить числом упаковок ({int(kg / prod[4][4])}).''',
     files=[f_db], hint='Количество упаковок умножь на «Количество в упаковке».')


def _ref803(wb):
    db_helpers(wb)
    ws = wb['Движение товаров']
    _helper(ws, 'L', 2, NM, lambda r: f'=F{r}*K{r}')
    return f'=SUMIFS({MV}!L2:L{NM},{MV}!I2:I{NM},"Сахар песок",{MV}!H2:H{NM},"Центральный",{MV}!E2:E{NM},"Продажа")'


reference('xl08-3', _ref803, f_db)

rev = sum(m[5] * m[6] for m in mv(lambda m: prod[m[3]][1] == 'Бакалея' and district_of[m[2]] == 'Северный' and m[4] == 'Продажа'))
task(L, 'xl08-4', 'Выручка отдела',
     DB_INTRO + 'Какова **выручка** (в рублях) от продажи всех товаров отдела **«Бакалея»** в магазинах **Северного** района '
     'за июнь? Выручка операции = количество упаковок × цена.', rev,
     HELP_TEXT + f'''

Столбец L «Выручка»: `=F2*G2`. Затем
`=СУММЕСЛИМН(L2:L{NM};J2:J{NM};"Бакалея";H2:H{NM};"Северный";E2:E{NM};"Продажа")` → **{rev}**.''',
     files=[f_db])


def _ref804(wb):
    db_helpers(wb)
    ws = wb['Движение товаров']
    _helper(ws, 'L', 2, NM, lambda r: f'=F{r}*G{r}')
    return f'=SUMIFS({MV}!L2:L{NM},{MV}!J2:J{NM},"Бакалея",{MV}!H2:H{NM},"Северный",{MV}!E2:E{NM},"Продажа")'


reference('xl08-4', _ref804, f_db)

by_district = {d: sum(m[5] for m in mv(lambda m: prod[m[3]][2] == 'Кефир 2,5%' and district_of[m[2]] == d and m[4] == 'Продажа'))
               for d in districts}
best_d = max(by_district, key=by_district.get)
assert list(by_district.values()).count(by_district[best_d]) == 1
task(L, 'xl08-5', 'В каком районе больше',
     DB_INTRO + 'В каком районе за июнь продали больше всего упаковок товара **«Кефир 2,5%»**? В ответе напиши название '
     'района (например, Центральный).', best_d,
     HELP_TEXT + '\n\nСтроим маленькую таблицу итогов на новом листе: в A2:A6 — названия районов, в B2:\n'
     f'`=СУММЕСЛИМН(\'Движение товаров\'!F$2:F${NM};\'Движение товаров\'!H$2:H${NM};A2;\'Движение товаров\'!I$2:I${NM};"Кефир 2,5%";'
     f'\'Движение товаров\'!E$2:E${NM};"Продажа")`, протягиваем на 5 районов.\n\n'
     + '\n'.join(f'- {d}: {v}' for d, v in by_district.items())
     + f'\n\nБольше всего — **{best_d}**. Формулой: `=ИНДЕКС(A2:A6;ПОИСКПОЗ(МАКС(B2:B6);B2:B6;0))`.',
     fmt='TEXT', files=[f_db])


def _ref805(wb):
    db_helpers(wb)
    ws = wb.create_sheet('Итоги')
    for i, d in enumerate(districts):
        ws.cell(i + 2, 1, d)
        ws.cell(i + 2, 2, f'=SUMIFS({MV}!F$2:F${NM},{MV}!H$2:H${NM},A{i + 2},{MV}!I$2:I${NM},"Кефир 2,5%",{MV}!E$2:E${NM},"Продажа")')
    return '=INDEX(Итоги!A2:A6,MATCH(MAX(Итоги!B2:B6),Итоги!B2:B6,0))'


reference('xl08-5', _ref805, f_db)

shops_north = [s[0] for s in shops_db if s[1] == 'Северный']
selling = {m[2] for m in mv(lambda m: m[2] in shops_north and prod[m[3]][2] == 'Хлеб белый' and m[4] == 'Продажа' and m[5] >= 100)}
task(L, 'xl08-6', 'Сколько магазинов',
     DB_INTRO + 'Сколько магазинов **Северного** района хотя бы раз продали за одну операцию **100 и больше** упаковок '
     'товара **«Хлеб белый»**?', len(selling),
     HELP_TEXT + f'''

Проще считать на листе «Магазин»: для каждого магазина — сколько у него подходящих операций.

1. В D2 листа «Магазин»:
   `=СЧЁТЕСЛИМН('Движение товаров'!C$2:C${NM};A2;'Движение товаров'!I$2:I${NM};"Хлеб белый";'Движение товаров'!E$2:E${NM};"Продажа";'Движение товаров'!F$2:F${NM};">=100")`
2. Магазин подходит, если он из Северного района и в D больше нуля:
   `=СЧЁТЕСЛИМН(B2:B19;"Северный";D2:D19;">0")` → **{len(selling)}**.''',
     files=[f_db])


def _ref806(wb):
    db_helpers(wb)
    ws = wb['Магазин']
    _helper(ws, 'D', 2, 19, lambda r: f'=COUNTIFS({MV}!C$2:C${NM},A{r},{MV}!I$2:I${NM},"Хлеб белый",{MV}!E$2:E${NM},"Продажа",{MV}!F$2:F${NM},">=100")')
    return '=COUNTIFS(Магазин!B2:B19,"Северный",Магазин!D2:D19,">0")'


reference('xl08-6', _ref806, f_db)

# ============================================================================ Урок 9 (задание 18)
L = lesson('09-task18')
THICK = Side(style='thick')
THIN = Side(style='thin', color='999999')


def make_field(seed, n, walls_count):
    rnd_ = random.Random(seed)
    grid = [[rnd_.randint(1, 99) for _ in range(n)] for _ in range(n)]
    walls = set()   # ('b', r, c) — стена под клеткой (r,c); ('r', r, c) — справа от клетки
    tries = 0
    while len(walls) < walls_count and tries < 10000:
        tries += 1
        kind = rnd_.choice('br')
        r, c = rnd_.randrange(n), rnd_.randrange(n)
        length = rnd_.randint(1, 3)
        seg = []
        for k in range(length):
            rr, cc = (r, c + k) if kind == 'b' else (r + k, c)
            if rr >= n - (1 if kind == 'b' else 0) or cc >= n - (1 if kind == 'r' else 0):
                break
            seg.append((kind, rr, cc))
        trial = walls | set(seg)
        if seg and all_reachable(n, trial):
            walls = trial
    return grid, walls


def preds(n, walls, r, c):
    out = []
    if r > 0 and ('b', r - 1, c) not in walls:
        out.append((r - 1, c))
    if c > 0 and ('r', r, c - 1) not in walls:
        out.append((r, c - 1))
    return out


def all_reachable(n, walls):
    return all(preds(n, walls, r, c) for r in range(n) for c in range(n) if (r, c) != (0, 0))


def robot(grid, walls, best):
    n = len(grid)
    dp = [[0] * n for _ in range(n)]
    for r in range(n):
        for c in range(n):
            p = preds(n, walls, r, c)
            dp[r][c] = grid[r][c] + (best(dp[a][b] for a, b in p) if p else 0)
    return dp[n - 1][n - 1]


def write_field(path, grid, walls):
    n = len(grid)
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = 'Лист1'
    for r in range(n):
        for c in range(n):
            cell = ws.cell(r + 1, c + 1, grid[r][c])
            cell.border = Border(left=THICK if c == 0 or ('r', r, c - 1) in walls else THIN,
                                 right=THICK if c == n - 1 or ('r', r, c) in walls else THIN,
                                 top=THICK if r == 0 or ('b', r - 1, c) in walls else THIN,
                                 bottom=THICK if r == n - 1 or ('b', r, c) in walls else THIN)
    for c in range(n):
        ws.column_dimensions[get_column_letter(c + 1)].width = 5
    wb.save(path)


def field_reference(id_, name, grid, walls):
    n = len(grid)

    def build(wb):
        ws = wb['Лист1']
        for which, off in (('MAX', n + 1), ('MIN', 2 * n + 2)):
            for r in range(n):
                for c in range(n):
                    p = preds(n, walls, r, c)
                    me = f'{get_column_letter(c + 1)}{r + 1}'
                    refs = [f'{get_column_letter(b + 1)}{a + 1 + off}' for a, b in p]
                    if not refs:
                        f_ = f'={me}'
                    elif len(refs) == 1:
                        f_ = f'={refs[0]}+{me}'
                    else:
                        f_ = f'={which}({refs[0]},{refs[1]})+{me}'
                    ws.cell(r + 1 + off, c + 1, f_)
        mx = f'{get_column_letter(n)}{n + n + 1}'
        mn = f'{get_column_letter(n)}{n + 2 * n + 2}'
        return f'=Лист1!{mx}&" "&Лист1!{mn}'
    reference(id_, build, name)


FIELD_TEXT = ('Квадрат разлинован на {n}×{n} клеток. Исполнитель Робот за один ход может переместиться на одну клетку '
              '**вправо** или **вниз**. Через стены (толстые линии) Робот проходить не может. В каждой клетке лежит монета '
              'достоинством от 1 до 99. Посетив клетку, Робот забирает монету, в том числе в начальной и конечной клетках.\n\n'
              'Определите **максимальную и минимальную** денежную сумму, которую может собрать Робот, пройдя из левой '
              'верхней клетки в правую нижнюю. В ответе запишите два числа через пробел: сначала максимум, потом минимум.')

for i, (n, wcount) in enumerate([(5, 0), (8, 0), (10, 6), (12, 9), (15, 12), (15, 18)], start=1):
    id_ = f'xl09-{i}'
    grid, walls = make_field(900 + i, n, wcount)
    name = f'{id_}.xlsx'
    write_field(FILES / name, grid, walls)
    mx, mn = robot(grid, walls, max), robot(grid, walls, min)
    off1, off2 = n + 1, 2 * n + 2
    wall_note = ('' if not walls else
                 f'\n\nВ поле есть стены. Там, где к клетке можно прийти только с одной стороны, в формуле **нет МАКС/МИН** — '
                 f'берём единственного соседа. Например, если стена над клеткой, формула только «слева»: '
                 f'`=A{off1 + 2}+B2` вместо `=МАКС(B{off1 + 1};A{off1 + 2})+B2`. Такие клетки удобно найти глазами и '
                 f'исправить формулу вручную после протягивания.\n\n![Стены](images/xl-robot-walls.svg)')
    task(L, id_, f'Робот: поле {n}×{n}' + (', стены' if walls else ''),
         FIELD_TEXT.format(n=n), f'{mx} {mn}',
         f'''Строим рядом с полем таблицу «лучшая сумма до клетки» — динамическое программирование.

1. Поле занимает A1:{get_column_letter(n)}{n}. Таблицу максимумов строим ниже, начиная со строки {off1 + 1}
   (одна пустая строка между ними).
2. **A{off1 + 1}** (старт): `=A1`.
3. Первая строка: **B{off1 + 1}**: `=A{off1 + 1}+B1` — прийти можно только слева. Протянуть вправо.
4. Первый столбец: **A{off1 + 2}**: `=A{off1 + 1}+A2` — только сверху. Протянуть вниз.
5. Остальные: **B{off1 + 2}**: `=МАКС(B{off1 + 1};A{off1 + 2})+B2` — лучший из соседей сверху и слева плюс своя монета.
   Протянуть на всё поле.{wall_note}
6. Максимум — в правой нижней клетке таблицы: **{mx}**.
7. Таблицу минимумов делаем так же со строки {off2 + 1}, заменив **МАКС** на **МИН**: **{mn}**.

Ответ: **{mx} {mn}**.''',
         fmt='INT_PAIR', files=[name], hint='Лучшая сумма до клетки = монета + МАКС(сверху; слева).')
    field_reference(id_, name, grid, walls)

# ============================================================================ Урок 10 (задание 22)
L = lesson('10-task22')


def make_processes(seed, n, max_deps):
    rnd_ = random.Random(seed)
    procs = []
    for pid in range(1, n + 1):
        k = 0 if pid == 1 else rnd_.randint(0, min(max_deps, pid - 1))
        deps = sorted(rnd_.sample(range(1, pid), k)) if k else []
        procs.append((pid, rnd_.randint(1, 15), deps))
    return procs


def finish_times(procs):
    fin = {}
    for pid, dur, deps in procs:
        fin[pid] = dur + max((fin[d] for d in deps), default=0)
    return fin


def write_procs(path, procs):
    rows = [[pid, dur, ';'.join(map(str, deps)) if deps else '0'] for pid, dur, deps in procs]
    write_book(path, [('Лист1', ['ID процесса B', 'Время выполнения процесса B (мс)', 'ID процесса(-ов) A'], rows)])


def procs_reference(id_, name, procs, answer_formula):
    def build(wb):
        ws = wb['Лист1']
        ws['D1'] = 'Окончание'
        for i, (pid, dur, deps) in enumerate(procs):
            r = i + 2
            ws[f'D{r}'] = f'=B{r}+MAX({",".join(f"D{d + 1}" for d in deps)})' if deps else f'=B{r}'
        return answer_formula(len(procs) + 1)
    reference(id_, build, name)


P_INTRO = ('В файле содержится информация о совокупности N вычислительных процессов, которые могут выполняться '
           'параллельно или последовательно. Будем говорить, что процесс B зависит от процесса A, если для выполнения '
           'процесса B необходимы результаты выполнения процесса A. В этом случае процессы могут выполняться только '
           'последовательно. Информация о процессах представлена в файле в виде таблицы. В первом столбце таблицы '
           'указан идентификатор процесса (ID), во втором столбце — время его выполнения в миллисекундах, в третьем '
           'столбце перечислены через «;» идентификаторы процессов, от которых зависит данный процесс. Если процесс '
           'независимый, то в таблице указано значение 0.\n\n')
P_SOLUTION = '''Добавляем столбец D «Время окончания»: процесс начинается, когда закончились **все** процессы, от которых он
зависит, поэтому

**окончание = своё время + МАКС(окончания всех процессов из столбца C)**.

1. Для независимого процесса (в C стоит 0): `=B2`.
2. Для зависимого — пишем ссылки на строки его зависимостей. Процесс с ID k стоит в строке k + 1. Например, если
   у процесса в строке 5 зависимости «1;3», формула `=B5+МАКС(D2;D4)`.
3. Формулы вписываем сверху вниз — зависимости всегда раньше по списку, значит, их окончание уже посчитано.

![Таблица](images/xl-task22-table.svg)
'''

p_tasks = [
    (1, 5, 1, 'Определите минимальное время (в мс), через которое завершится выполнение **всей** совокупности процессов, '
              'при условии, что все независимые друг от друга процессы могут выполняться параллельно.', 'all'),
    (2, 10, 2, None, 'all'),
    (3, 15, 3, None, 'all'),
    (4, 20, 3, 'Сколько процессов **завершится** к моменту времени **30 мс** (включительно), если все независимые '
               'процессы выполняются параллельно и каждый начинается как можно раньше?', 't30'),
    (5, 25, 3, 'Через какое минимальное время (в мс) завершится процесс с **ID 17**, если независимые процессы '
               'выполняются параллельно?', 'p17'),
    (6, 30, 4, None, 'all'),
]
for i, n, deps_max, question, kind in p_tasks:
    id_ = f'xl10-{i}'
    procs = make_processes(1000 + i, n, deps_max)
    fin = finish_times(procs)
    name = f'{id_}.xlsx'
    write_procs(FILES / name, procs)
    question = question or p_tasks[0][3]
    if kind == 'all':
        ans = max(fin.values())
        how = f'Ответ — наибольшее время окончания: `=МАКС(D2:D{n + 1})` → **{ans}**.'
        af = lambda last: f'=MAX(Лист1!D2:D{last})'
    elif kind == 't30':
        ans = sum(1 for v in fin.values() if v <= 30)
        assert 0 < ans < n
        how = f'Считаем процессы, у которых время окончания не больше 30: `=СЧЁТЕСЛИ(D2:D{n + 1};"<=30")` → **{ans}**.'
        af = lambda last: f'=COUNTIF(Лист1!D2:D{last},"<=30")'
    else:
        ans = fin[17]
        how = f'Ответ — время окончания процесса 17, ячейка **D18**: **{ans}**.'
        af = lambda last: '=Лист1!D18'
    deps_example = next(p for p in procs if len(p[2]) >= 2) if any(len(p[2]) >= 2 for p in procs) else procs[-1]
    task(L, id_, f'Параллельные процессы: {n} шт.', P_INTRO + question, ans,
         P_SOLUTION + f'''
В этом файле, например, процесс {deps_example[0]} зависит от {", ".join(map(str, deps_example[2])) or "никого"}:
его окончание `=B{deps_example[0] + 1}{"+МАКС(" + ";".join(f"D{d + 1}" for d in deps_example[2]) + ")" if deps_example[2] else ""}` = {fin[deps_example[0]]}.

{how}''',
         files=[name], hint='Окончание процесса = его время + МАКС(окончания зависимостей).')
    procs_reference(id_, name, procs, af)


# ============================================================================ запись

def main():
    TASKS.mkdir(parents=True, exist_ok=True)
    ids = set()
    total = 0
    for les in LESSONS:
        for t in les['tasks']:
            assert t['id'] not in ids, t['id']
            ids.add(t['id'])
            assert t['id'] in MANIFEST, f"нет перепроверки для {t['id']}"
            total += 1
        (TASKS / f"{les['slug']}.json").write_text(
            json.dumps({'lesson': les['slug'], 'tasks': les['tasks']}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (REF / 'manifest.json').write_text(json.dumps(MANIFEST, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    used = {f for les in LESSONS for t in les['tasks'] for f in t.get('files', [])}
    for p in FILES.glob('*.xlsx'):
        if p.name not in used:
            p.unlink()
    print(f'Уроков: {len(LESSONS)}, задач: {total}, файлов: {len(used)}, '
          f'перепроверка LibreOffice: {sum(1 for m in MANIFEST.values() if m["method"] == "libreoffice")}, '
          f'Translator: {sum(1 for m in MANIFEST.values() if m["method"] == "translate")}, '
          f'вручную: {sum(1 for m in MANIFEST.values() if m["method"] == "manual")}')


if __name__ == '__main__':
    main()
