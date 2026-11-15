"""SVG-картинки курса «Excel для ЕГЭ»: фрагменты листа, как в настоящем Excel.
Главная функция — sheet(): заголовки столбцов и строк, сетка, строка формул с полем имени, активная ячейка
(зелёная рамка), подсвеченные диапазоны, стрелки «ячейка ссылается сюда», выноски. before_after() ставит
два листа рядом со стрелкой.

Запуск: python course-tools/excel/gen_excel_images.py
"""
import math
import re
from html import escape
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'courses' / 'excel-ege' / 'images'

SANS = 'Segoe UI, Calibri, Arial, sans-serif'
MONO = "Consolas, 'Courier New', monospace"
INK = '#1f2328'
GRID = '#d4d4d4'
HEAD_BG = '#f3f3f3'
HEAD_TX = '#444'
EXCEL_GREEN = '#107c41'
RED = '#c92a2a'
BLUE = '#1c62c9'
GREEN = '#2b8a3e'
ORANGE = '#e8590c'
PURPLE = '#7048e8'
YELLOW = '#f59f00'
GRAY = '#868e96'
FILL = {BLUE: '#1c62c9', GREEN: '#2b8a3e', ORANGE: '#e8590c', PURPLE: '#7048e8', RED: '#c92a2a', YELLOW: '#f59f00'}

images = {}


def t(x, y, s, size=14, fill=INK, weight='normal', anchor='start', font=None):
    f = f' font-family="{font}"' if font else ''
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}" font-weight="{weight}" '
            f'text-anchor="{anchor}"{f} xml:space="preserve">{escape(str(s))}</text>')


def rect(x, y, w, h, fill='#fff', stroke=GRID, sw=1, rx=0, opacity=None, dash=None):
    o = f' fill-opacity="{opacity}"' if opacity is not None else ''
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" fill="{fill}"{o} '
            f'stroke="{stroke}" stroke-width="{sw}"{d}/>')


def line(x1, y1, x2, y2, color=INK, sw=2, arrow=None, dash=None):
    m = f' marker-end="url(#a-{arrow})"' if arrow else ''
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{color}" stroke-width="{sw}"{m}{d}/>'


def defs():
    out = '<defs>'
    for name, color in [('ink', INK), ('red', RED), ('blue', BLUE), ('green', GREEN), ('orange', ORANGE),
                        ('purple', PURPLE), ('gray', GRAY)]:
        out += (f'<marker id="a-{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
                f'orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="{color}"/></marker>')
    return out + '</defs>'


ARROW_NAME = {INK: 'ink', RED: 'red', BLUE: 'blue', GREEN: 'green', ORANGE: 'orange', PURPLE: 'purple', GRAY: 'gray'}


def svg(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} {h:.0f}" width="{w:.0f}" height="{h:.0f}" '
            f'font-family="{SANS}">\n<rect width="{w:.0f}" height="{h:.0f}" fill="#ffffff"/>\n{defs()}\n{body}\n</svg>\n')


def col_name(i):
    s = ''
    i += 1
    while i:
        i, r = divmod(i - 1, 26)
        s = chr(65 + r) + s
    return s


def parse(addr):
    m = re.fullmatch(r'\$?([A-Z]+)\$?(\d+)', addr)
    c = 0
    for ch in m.group(1):
        c = c * 26 + ord(ch) - 64
    return int(m.group(2)) - 1, c - 1


class Sheet:
    """Фрагмент листа. Координаты ячеек — по адресу, например 'B3'."""

    def __init__(self, x, y, cols, rows, first_col=0, first_row=0, widths=None, cell_h=26, head_w=36,
                 formula=None, name=None, title=None, font=14):
        self.x, self.y = x, y
        self.cols, self.rows = cols, rows
        self.fc, self.fr = first_col, first_row
        self.widths = widths or [74] * cols
        self.ch = cell_h
        self.hw = head_w
        self.font = font
        self.parts = []
        self.top = y
        if title:
            self.parts.append(t(x, y + 16, title, 15, INK, 'bold'))
            self.top += 26
        if formula is not None or name is not None:
            fw = self.hw + sum(self.widths)
            by = self.top
            self.parts.append(rect(x, by, 70, 26, '#fff', '#b5b5b5'))
            self.parts.append(t(x + 8, by + 18, name or '', 13, INK, 'normal', 'start', MONO))
            self.parts.append(t(x + 84, by + 18, 'fx', 14, GRAY, 'bold', 'start', 'Cambria, serif'))
            self.parts.append(rect(x + 108, by, max(fw - 108, 120), 26, '#fff', '#b5b5b5'))
            self.parts.append(t(x + 116, by + 18, formula or '', 13, INK, 'normal', 'start', MONO))
            self.top += 34
        self.gx = x + self.hw
        self.gy = self.top + 22
        self.cells = {}
        self.styles = {}

    # геометрия
    def cell_box(self, addr):
        r, c = parse(addr)
        r -= self.fr
        c -= self.fc
        cx = self.gx + sum(self.widths[:c])
        return cx, self.gy + r * self.ch, self.widths[c], self.ch

    def range_box(self, rng):
        a, b = (rng.split(':') + [rng])[:2]
        x1, y1, _, _ = self.cell_box(a)
        x2, y2, w2, h2 = self.cell_box(b)
        return x1, y1, x2 + w2 - x1, y2 + h2 - y1

    def center(self, addr):
        x, y, w, h = self.cell_box(addr)
        return x + w / 2, y + h / 2

    @property
    def width(self):
        return self.hw + sum(self.widths)

    @property
    def bottom(self):
        return self.gy + self.rows * self.ch

    # содержимое
    def set(self, addr, value, color=None, bold=False, align=None, bg=None, font=None):
        self.cells[addr] = value
        self.styles[addr] = (color, bold, align, bg, font)
        return self

    def fill(self, start, rows_values):
        r0, c0 = parse(start)
        for i, row in enumerate(rows_values):
            for j, v in enumerate(row):
                if v is not None and v != '':
                    self.set(col_name(c0 + j) + str(r0 + i + 1), v)
        return self

    def render(self, active=None, ranges=(), arrows=(), notes=(), marks=(), thick=()):
        out = list(self.parts)
        gx, gy = self.gx, self.gy
        # заголовки
        out.append(rect(self.x, gy - 22, self.hw, 22, HEAD_BG, GRID))
        ar, ac = parse(active) if active else (-1, -1)
        for j in range(self.cols):
            cx = gx + sum(self.widths[:j])
            hl = self.fc + j == ac
            out.append(rect(cx, gy - 22, self.widths[j], 22, '#d2e7d9' if hl else HEAD_BG, GRID))
            out.append(t(cx + self.widths[j] / 2, gy - 6, col_name(self.fc + j), 13,
                         EXCEL_GREEN if hl else HEAD_TX, 'bold' if hl else 'normal', 'middle'))
        for i in range(self.rows):
            hl = self.fr + i == ar
            out.append(rect(self.x, gy + i * self.ch, self.hw, self.ch, '#d2e7d9' if hl else HEAD_BG, GRID))
            out.append(t(self.x + self.hw / 2, gy + i * self.ch + self.ch / 2 + 5, self.fr + i + 1, 13,
                         EXCEL_GREEN if hl else HEAD_TX, 'bold' if hl else 'normal', 'middle'))
        # сетка и фоны ячеек
        for i in range(self.rows):
            for j in range(self.cols):
                addr = col_name(self.fc + j) + str(self.fr + i + 1)
                bg = self.styles.get(addr, (None,) * 5)[3]
                cx = gx + sum(self.widths[:j])
                out.append(rect(cx, gy + i * self.ch, self.widths[j], self.ch, bg or '#fff', GRID))
        for rng, color in ranges:
            x, y, w, h = self.range_box(rng)
            out.append(rect(x, y, w, h, FILL.get(color, color), color, 2, 0, 0.14))
        for addr in thick:   # «стены» задания 18: (адрес, сторона)
            a, side = addr
            x, y, w, h = self.cell_box(a)
            seg = {'b': (x, y + h, x + w, y + h), 'r': (x + w, y, x + w, y + h),
                   't': (x, y, x + w, y), 'l': (x, y, x, y + h)}[side]
            out.append(line(*seg, INK, 4))
        # значения
        for addr, value in self.cells.items():
            color, bold, align, _, font = self.styles[addr]
            x, y, w, h = self.cell_box(addr)
            text = str(value)
            is_num = isinstance(value, (int, float)) or re.fullmatch(r'-?\d+([.,]\d+)?', text) is not None
            anchor = align or ('end' if is_num else 'start')
            tx = x + w - 6 if anchor == 'end' else x + w / 2 if anchor == 'middle' else x + 6
            mono = font or (MONO if text.startswith('=') or text.startswith('#') else None)
            out.append(t(tx, y + h / 2 + 5, text, self.font - (1 if mono else 0), color or INK,
                         'bold' if bold else 'normal', anchor, mono))
        for addr, color in marks:
            x, y, w, h = self.cell_box(addr)
            out.append(rect(x + 1, y + 1, w - 2, h - 2, 'none', color, 2.5))
        if active:
            x, y, w, h = self.cell_box(active)
            out.append(rect(x, y, w, h, 'none', EXCEL_GREEN, 3))
            out.append(rect(x + w - 4, y + h - 4, 7, 7, EXCEL_GREEN, '#fff', 1))
        for frm, to, color in arrows:
            out.append(self.arrow(frm, to, color))
        for x, y, text, color, tx, ty in notes:
            out.append(callout(x, y, text, color, tx, ty))
        return '\n'.join(out)


def _edge(box, dx, dy):
    """Точка на границе ячейки в направлении (dx, dy) от её центра."""
    x, y, w, h = box
    cx, cy = x + w / 2, y + h / 2
    k = min(w / 2 / abs(dx) if dx else 1e9, h / 2 / abs(dy) if dy else 1e9)
    return cx + dx * k, cy + dy * k


def _arrow(self, frm, to, color):
    """Стрелка «ячейка frm ссылается в to»: от края до края; через ячейки одной строки — дугой сверху."""
    bf, bt = self.cell_box(frm), self.cell_box(to)
    (x1, y1), (x2, y2) = self.center(frm), self.center(to)
    name = ARROW_NAME.get(color, 'ink')
    rf, cf = parse(frm)
    rt, ct = parse(to)
    if cf == ct and abs(rt - rf) > 1:
        sx, sy = bf[0] + bf[2] - 3, y1
        ex, ey = bt[0] + bt[2] - 3, y2
        bulge = 18 + abs(ey - sy) * 0.15
        return (f'<path d="M {sx:.1f} {sy:.1f} Q {sx + bulge * 2:.1f} {(sy + ey) / 2:.1f} {ex + 2:.1f} {ey:.1f}" '
                f'fill="none" stroke="{color}" stroke-width="2.2" marker-end="url(#a-{name})"/>'
                f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="3.5" fill="{color}"/>')
    if rf == rt and abs(ct - cf) > 1:
        sx, sy = x1, bf[1] + 3
        ex, ey = x2, bt[1] + 3
        lift = 16 + abs(ex - sx) * 0.08
        return (f'<path d="M {sx:.1f} {sy:.1f} Q {(sx + ex) / 2:.1f} {sy - lift * 2:.1f} {ex:.1f} {ey - 2:.1f}" '
                f'fill="none" stroke="{color}" stroke-width="2.2" marker-end="url(#a-{name})"/>'
                f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="3.5" fill="{color}"/>')
    dx, dy = x2 - x1, y2 - y1
    sx, sy = _edge(bf, dx, dy)
    ex, ey = _edge(bt, -dx, -dy)
    d = math.hypot(ex - sx, ey - sy) or 1
    ex, ey = ex - (ex - sx) / d * 2, ey - (ey - sy) / d * 2
    return line(sx, sy, ex, ey, color, 2.2, name) + f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="3.5" fill="{color}"/>'


Sheet.arrow = _arrow


def callout(x, y, text, color, tx=None, ty=None):
    w = len(text) * 7.4 + 20
    out = ''
    if tx is not None:
        out += line(x + w / 2 if tx > x + w else x, y - 6, tx, ty, color, 2, ARROW_NAME.get(color, 'ink'))
    out += rect(x - 4, y - 19, w, 26, color, color, 1, 6)
    out += t(x + 6, y, text, 13, '#fff', 'bold')
    return out


def save(name, w, h, body):
    images[name] = svg(w, h, body)


def before_after(left, right, title_l='Было', title_r='Стало', gap=70):
    """Два листа рядом со стрелкой. left/right — (Sheet, render kwargs)."""
    sl, kl = left
    sr, kr = right
    body = sl.render(**kl) + sr.render(**kr)
    mid_y = (sl.gy + sl.bottom) / 2
    ax = sl.x + sl.width + 12
    body += line(ax, mid_y, ax + gap - 24, mid_y, ORANGE, 4, 'orange')
    body += t(sl.x, sl.top - 8 if sl.top > 20 else 14, title_l, 15, GRAY, 'bold')
    body += t(sr.x, sr.top - 8 if sr.top > 20 else 14, title_r, 15, GREEN, 'bold')
    return body


# =========================================================================== Урок 1

# Интерфейс: схема окна с подписями
b = rect(20, 20, 860, 420, '#fff', '#b5b5b5', 1.5, 6)
b += rect(20, 20, 860, 30, EXCEL_GREEN, EXCEL_GREEN, 1, 6) + t(40, 41, 'Книга1 — Excel', 14, '#fff', 'bold')
b += rect(20, 50, 860, 26, '#f3f3f3', '#e0e0e0') + ''.join(
    t(40 + i * 92, 68, w, 13, INK) for i, w in enumerate(['Файл', 'Главная', 'Вставка', 'Формулы', 'Данные', 'Вид']))
b += rect(20, 76, 860, 56, '#fafafa', '#e0e0e0')
for i in range(9):
    b += rect(36 + i * 64, 84, 50, 26, '#fff', '#d0d0d0', 1, 4)
s = Sheet(20, 140, 9, 9, widths=[86] * 9, formula='=B3*2', name='C3')
s.fill('A1', [['Имя', 'Балл', 'Удвоено'], ['Аня', 45, 90], ['Борис', 38, 76], ['Вера', 50, 100]])
b += s.render(active='C3')
b += rect(20, 420, 860, 20, '#f3f3f3', '#e0e0e0') + rect(60, 421, 70, 18, '#fff', EXCEL_GREEN) + t(70, 435, 'Лист1', 12, EXCEL_GREEN, 'bold')
b += callout(640, 110, 'лента с вкладками', BLUE, 560, 100)
b += callout(330, 175, 'строка формул', PURPLE, 260, 156)
b += callout(150, 131, 'поле имени: адрес активной ячейки', ORANGE, 70, 150)
b += callout(560, 300, 'активная ячейка C3', GREEN, 330, 270)
b += callout(180, 412, 'ярлыки листов', RED, 98, 428)
b += callout(700, 230, 'столбец D', BLUE, 354, 205)
b += callout(700, 390, 'строка 7', BLUE, 56, 365)
save('xl-interface.svg', 900, 460, b)

s = Sheet(20, 20, 6, 6, name='B3', formula='')
b = s.render(active='B3', ranges=[('B1:B6', BLUE), ('A3:F3', ORANGE)])
b += callout(500, 60, 'столбец B', BLUE, 160, 70) + callout(500, 160, 'строка 3', ORANGE, 470, 130)
b += callout(500, 220, 'ячейка B3 = столбец B + строка 3', GREEN, 160, 140)
save('xl-address.svg', 760, 250, b)

s = Sheet(20, 20, 6, 7, name='A1', formula='')
for r in range(1, 6):
    for c in range(3):
        s.set(col_name(c) + str(r), (r - 1) * 3 + c + 1)
b = s.render(active='A1', ranges=[('A1:C5', BLUE)])
b += callout(400, 80, 'диапазон A1:C5', BLUE, 260, 100)
b += callout(400, 150, '5 строк × 3 столбца = 15 ячеек', GRAY)
save('xl-range.svg', 700, 260, b)

s1 = Sheet(20, 30, 3, 6, name='A1', formula='1')
s1.set('A1', 1)
s2 = Sheet(380, 30, 3, 6, name='A6', formula='6')
for i in range(6):
    s2.set(f'A{i + 1}', i + 1)
b = before_after((s1, {'active': 'A1', 'notes': [(130, 140, 'тянем за маркер ↓', EXCEL_GREEN, 118, 90)]}),
                 (s2, {'active': 'A6', 'ranges': [('A1:A6', GREEN)]}), 'Ввели 1 и 2, выделили', 'Протянули вниз')
s1.set('A2', 2)
b = before_after((s1, {'ranges': [('A1:A2', BLUE)], 'active': 'A2'}), (s2, {'active': 'A6', 'ranges': [('A1:A6', GREEN)]}),
                 'Ввели 1 и 2, выделили обе', 'Потянули маркер вниз')
b += callout(110, 270, 'маркер — квадратик в углу', EXCEL_GREEN, 120, 116)
save('xl-autofill.svg', 720, 300, b)

s = Sheet(20, 20, 3, 4, widths=[110, 90, 90], name='A1')
s.set('A1', 'Москва').set('A2', 2026).set('A3', '15.11.2026', align='end').set('A4', '1+2')
b = s.render(active='A1')
b += callout(380, 70, 'текст — к левому краю', BLUE, 160, 78)
b += callout(380, 110, 'число — к правому', GREEN, 160, 104)
b += callout(380, 150, 'дата — это тоже число', ORANGE, 160, 130)
b += callout(380, 190, 'без «=» это просто текст', RED, 160, 156)
save('xl-data-types.svg', 680, 210, b)

# Задача xl01-1: какая ячейка активна
s = Sheet(20, 20, 6, 7, name='', formula='')
s.fill('A1', [['', 12, 7, 30], [5, '', 18], ['', 44, '', 9], [3, '', 21, 8]])
save('xl01-1-active.svg', 520, 280, s.render(active='C4'))

# =========================================================================== Урок 2

s = Sheet(20, 20, 4, 3, name='C1', formula='=A1+B1')
s.set('A1', 7).set('B1', 8).set('C1', 15)
b = s.render(active='C1', arrows=[('A1', 'C1', BLUE), ('B1', 'C1', BLUE)])
b += callout(380, 40, 'в строке формул — формула', PURPLE, 300, 34)
b += callout(380, 120, 'в ячейке — результат', GREEN, 280, 74)
save('xl-formula.svg', 640, 140, b)

s1 = Sheet(20, 30, 3, 3, name='C1', formula='=A1*B1')
s1.set('A1', 4).set('B1', 5).set('C1', 20)
s2 = Sheet(360, 30, 3, 3, name='C1', formula='=A1*B1')
s2.set('A1', 10, RED, True).set('B1', 5).set('C1', 50, GREEN, True)
b = before_after((s1, {'active': 'C1'}), (s2, {'active': 'C1', 'marks': [('A1', RED)]}), 'A1 = 4', 'Поменяли A1 на 10')
b += t(360, 170, 'Формула та же — результат пересчитался сам', 14, GREEN, 'bold')
save('xl-recalc.svg', 680, 190, b)

s = Sheet(20, 20, 2, 5, widths=[150, 360], font=13)
rows = [('#ДЕЛ/0!', 'деление на ноль или на пустую ячейку'), ('#ИМЯ?', 'опечатка в имени функции или текст без кавычек'),
        ('#ЗНАЧ!', 'число смешали с текстом: ="5 кг"+1'), ('#ССЫЛКА!', 'ячейку, на которую ссылались, удалили'),
        ('#Н/Д', 'ВПР/ПОИСКПОЗ не нашли значение')]
for i, (e, d) in enumerate(rows):
    s.set(f'A{i + 1}', e, RED, True).set(f'B{i + 1}', d)
save('xl-errors.svg', 560, 170, s.render())

# =========================================================================== Урок 3

s = Sheet(20, 20, 4, 6, widths=[60, 60, 150, 60], name='C5', formula='=A5*B5')
for i in range(1, 6):
    s.set(f'A{i + 1}', i * 2).set(f'B{i + 1}', 3).set(f'C{i + 1}', f'=A{i + 1}*B{i + 1}')
s.set('A1', 'цена', bold=True).set('B1', 'кол-во', bold=True).set('C1', 'формула', bold=True)
b = s.render(active='C5', arrows=[('A5', 'C5', BLUE), ('B5', 'C5', ORANGE)],
             ranges=[('C2:C6', GREEN)])
b += callout(420, 120, 'у каждой строки — своя формула', GREEN, 360, 120)
b += callout(420, 190, 'в C5 формула смотрит на A5 и B5', BLUE, 340, 200)
save('xl-relative.svg', 720, 240, b)

s = Sheet(20, 20, 4, 6, widths=[60, 60, 150, 60], name='C4', formula='=A4*$E$1')
s.set('E1', 92) if False else None
for i in range(2, 7):
    s.set(f'A{i}', i * 10).set(f'C{i}', f'=A{i}*$D$1')
s.set('D1', 92, RED, True).set('C1', 'в рублях', bold=True).set('A1', '$', bold=True)
b = s.render(active='C4', arrows=[(f'D1', f'C{i}', RED) for i in range(2, 7)], marks=[('D1', RED)])
b += callout(430, 60, 'курс в D1 — один на всех', RED, 380, 42)
b += callout(430, 150, '$D$1 не сдвигается', RED)
save('xl-absolute.svg', 720, 220, b)

s = Sheet(20, 20, 4, 5, widths=[120, 170, 170, 0.1], font=13)
s.set('A1', 'ссылка', bold=True).set('B1', 'копируем вниз ↓', bold=True).set('C1', 'копируем вправо →', bold=True)
for i, (ref, down, right) in enumerate([('A1', 'A2 (меняется)', 'B1 (меняется)'), ('$A$1', '$A$1 (нет)', '$A$1 (нет)'),
                                        ('$A1', '$A2 (строка)', '$A1 (нет)'), ('A$1', 'A$1 (нет)', 'B$1 (столбец)')]):
    s.set(f'A{i + 2}', ref, BLUE, True, font=MONO).set(f'B{i + 2}', down).set(f'C{i + 2}', right)
save('xl-ref-types.svg', 540, 170, s.render())

s = Sheet(20, 20, 6, 6, widths=[50] * 6, name='C3', formula='=$A3*C$1')
for i in range(1, 6):
    s.set(col_name(i) + '1', i, BLUE, True, bg='#e7f0fb')
    s.set('A' + str(i + 1), i, ORANGE, True, bg='#fff4e6')
for r in range(1, 6):
    for c in range(1, 6):
        s.set(col_name(c) + str(r + 1), r * c)
b = s.render(active='C3', arrows=[('A3', 'C3', ORANGE), ('C1', 'C3', BLUE)])
b += callout(400, 60, '$A3: столбец A закреплён', ORANGE, 100, 141)
b += callout(400, 110, 'C$1: строка 1 закреплена', BLUE, 206, 89)
b += callout(400, 160, 'одна формула на всю таблицу', GREEN)
save('xl-mult-table.svg', 700, 200, b)

s1 = Sheet(20, 30, 5, 6, widths=[56] * 5)
s1.set('C2', '=A1+$B2', font=MONO)
s2 = Sheet(380, 30, 5, 6, widths=[56] * 5)
s2.set('E5', '=C4+$B5', GREEN, True, font=MONO)
s2.set('C2', '=A1+$B2', GRAY, font=MONO)
b = before_after((s1, {'active': 'C2'}), (s2, {'active': 'E5', 'arrows': [('C2', 'E5', ORANGE)]}),
                 'Формула в C2', 'Скопировали в E5: +2 столбца, +3 строки')
b += t(380, 245, 'A1 → C4 (обе части сдвинулись), $B2 → $B5 (столбец закреплён)', 13, INK)
save('xl-copy-shift.svg', 760, 260, b)

# =========================================================================== Урок 4

s = Sheet(20, 20, 3, 7, widths=[110, 60, 60], name='C7', formula='=СРЗНАЧ(B2:B6)')
s.set('A1', 'Ученик', bold=True).set('B1', 'Балл', bold=True)
for i, (n, v) in enumerate([('Аня', 72), ('Борис', 58), ('Вера', 91), ('Глеб', 64), ('Даша', 85)]):
    s.set(f'A{i + 2}', n).set(f'B{i + 2}', v)
s.set('A7', 'Среднее', bold=True).set('C7', 74, GREEN, True)
b = s.render(active='C7', ranges=[('B2:B6', BLUE)])
b += callout(320, 110, 'аргумент — диапазон B2:B6', BLUE, 230, 120)
b += callout(320, 200, 'СРЗНАЧ = сумма / количество', GREEN, 292, 200)
save('xl-function.svg', 640, 230, b)

s = Sheet(20, 20, 4, 7, widths=[90, 60, 60, 120], name='D2', formula='=СЧЁТЕСЛИ(B2:B7;">=60")')
s.set('A1', 'Ученик', bold=True).set('B1', 'Балл', bold=True)
data = [('Аня', 72), ('Борис', 58), ('Вера', 91), ('Глеб', 44), ('Даша', 85), ('Егор', 60)]
for i, (n, v) in enumerate(data):
    s.set(f'A{i + 2}', n).set(f'B{i + 2}', v, GREEN if v >= 60 else GRAY, v >= 60)
s.set('D2', 4, GREEN, True)
b = s.render(active='D2', marks=[(f'B{i + 2}', GREEN) for i, (_, v) in enumerate(data) if v >= 60])
b += callout(400, 120, 'зелёные подходят под ">=60": их 4', GREEN, 210, 120)
save('xl-countif.svg', 720, 220, b)

s = Sheet(20, 20, 3, 6, widths=[70, 60, 120], name='C2', formula='=ОСТАТ(B2;2)')
for i, v in enumerate([17, 24, 9, 30, 12]):
    s.set(f'B{i + 2}', v).set(f'C{i + 2}', v % 2, GREEN if v % 2 == 0 else GRAY, True)
s.set('B1', 'число', bold=True).set('C1', 'остаток от /2', bold=True)
b = s.render(active='C2', ranges=[('C2:C6', ORANGE)])
b += callout(320, 100, 'вспомогательный столбец', ORANGE, 290, 110)
b += callout(320, 160, 'чётных = СЧЁТЕСЛИ(C2:C6;0) = 3', GREEN)
save('xl-helper-mod.svg', 660, 200, b)

# =========================================================================== Урок 5

b = rect(260, 20, 220, 50, '#e7f0fb', BLUE, 2, 25) + t(370, 51, 'условие: B2 >= 40 ?', 15, BLUE, 'bold', 'middle')
b += line(320, 70, 200, 130, GREEN, 2.5, 'green') + t(220, 100, 'ИСТИНА', 13, GREEN, 'bold')
b += line(420, 70, 540, 130, RED, 2.5, 'red') + t(490, 100, 'ЛОЖЬ', 13, RED, 'bold')
b += rect(110, 132, 180, 46, '#d3f9d8', GREEN, 2, 8) + t(200, 161, '"сдал"', 16, GREEN, 'bold', 'middle')
b += rect(450, 132, 180, 46, '#ffe3e3', RED, 2, 8) + t(540, 161, '"не сдал"', 16, RED, 'bold', 'middle')
b += t(370, 215, '=ЕСЛИ(B2>=40; "сдал"; "не сдал")', 17, INK, 'bold', 'middle', MONO)
save('xl-if.svg', 740, 235, b)

s = Sheet(20, 20, 5, 6, widths=[80, 60, 60, 60, 80], name='E2', formula='=ЕСЛИ(И(B2>=40;C2>=40;D2>=40);1;0)')
s.set('A1', 'Ученик', bold=True).set('B1', 'Мат', bold=True).set('C1', 'Рус', bold=True).set('D1', 'Инф', bold=True).set('E1', 'все ≥40', bold=True)
rows = [('Аня', 55, 70, 62), ('Борис', 38, 81, 47), ('Вера', 91, 64, 88), ('Глеб', 45, 39, 51)]
for i, (n, *v) in enumerate(rows):
    s.set(f'A{i + 2}', n)
    for j, x in enumerate(v):
        s.set(col_name(j + 1) + str(i + 2), x, RED if x < 40 else None, x < 40)
    ok = int(all(x >= 40 for x in v))
    s.set(f'E{i + 2}', ok, GREEN if ok else RED, True)
s.set('E6', '=СУММ(E2:E5) → 2', GREEN, True, font=MONO)
b = s.render(active='E2', ranges=[('E2:E5', ORANGE)])
b += callout(430, 100, 'столбец из 1 и 0', ORANGE, 400, 110)
save('xl-and-helper.svg', 700, 230, b)

# =========================================================================== Урок 6

s1 = Sheet(20, 30, 2, 5, widths=[90, 60])
s2 = Sheet(300, 30, 2, 5, widths=[90, 60])
names = [('Вера', 91), ('Аня', 72), ('Глеб', 64), ('Борис', 58)]
for i, (n, v) in enumerate(names):
    s1.set(f'A{i + 2}', n).set(f'B{i + 2}', v)
s1.set('A1', 'Ученик', bold=True).set('B1', 'Балл', bold=True)
sorted_wrong = sorted(n for n, _ in names)
for i, n in enumerate(sorted_wrong):
    s2.set(f'A{i + 2}', n, RED, True).set(f'B{i + 2}', names[i][1])
s2.set('A1', 'Ученик', bold=True).set('B1', 'Балл', bold=True)
b = before_after((s1, {'ranges': [('A2:A5', BLUE)]}), (s2, {'ranges': [('A2:A5', RED)]}),
                 'Выделили только столбец A', 'Отсортировали — баллы остались на месте!')
b += t(300, 200, 'Теперь у Ани 91 балл, хотя было 72. Данные испорчены.', 14, RED, 'bold')
save('xl-sort-wrong.svg', 620, 220, b)

s = Sheet(20, 20, 4, 6, widths=[60, 100, 100, 80])
s.fill('A1', [['ID', 'Город ▾', 'Менеджер ▾', 'Сумма ▾'], [3, 'Казань', 'Орлов', 7200], [7, 'Казань', 'Ильина', 5400],
              [12, 'Казань', 'Орлов', 9100], [15, 'Казань', 'Ким', 6300]])
for c in 'ABCD':
    s.set(c + '1', s.cells[c + '1'], bold=True, bg='#e7f0fb')
b = s.render(ranges=[('D2:D5', GREEN)])
b += callout(420, 70, 'кнопки ▾ — фильтр', BLUE, 380, 34) + callout(420, 140, 'показаны только строки Казани', GRAY)
b += rect(20, 200, 400, 24, '#f3f3f3', '#d0d0d0') + t(30, 217, 'Среднее: 7000   Количество: 4   Сумма: 28000', 13, INK)
b += callout(440, 214, 'строка состояния', GREEN, 420, 212)
save('xl-filter.svg', 720, 240, b)

s = Sheet(20, 20, 2, 8, widths=[60, 80])
vals = [14, 22, 14, 9, 31, 22, 5, 14]
for i, v in enumerate(vals):
    dup = vals.count(v) > 1
    s.set(f'B{i + 1}', v, '#9c0006' if dup else None, dup, bg='#ffc7ce' if dup else None)
b = s.render(ranges=[('B1:B8', BLUE)])
b += callout(220, 80, 'повторы подсвечены красным', RED, 190, 60)
b += callout(220, 150, 'Главная → Условное форматирование', BLUE)
save('xl-duplicates.svg', 560, 250, b)

# =========================================================================== Урок 7

s = Sheet(20, 20, 9, 5, widths=[44] * 6 + [64, 64, 64], font=13)
rows = [[5, 12, 5, 7, 3, 9], [8, 8, 1, 4, 8, 2], [6, 10, 2, 6, 11, 4], [1, 2, 3, 4, 5, 6]]
for i, row in enumerate(rows):
    for j, v in enumerate(row):
        s.set(col_name(j) + str(i + 1), v)
    counts = [row.count(x) for x in row]
    pattern = sorted(counts) == [1, 1, 1, 1, 2, 2]
    rep = [x for x in row if row.count(x) == 2]
    rest = [x for x in row if row.count(x) == 1]
    avg_ok = pattern and sum(rest) / len(rest) <= rep[0]
    s.set(f'G{i + 1}', int(pattern), GREEN if pattern else RED, True, 'middle')
    s.set(f'H{i + 1}', round(sum(rest) / len(rest), 1) if pattern else '—', align='middle')
    s.set(f'I{i + 1}', int(avg_ok), GREEN if avg_ok else RED, True, 'middle')
b = s.render(ranges=[('A1:F4', BLUE), ('G1:G4', ORANGE), ('H1:H4', PURPLE), ('I1:I4', GREEN)])
b += t(56, 180, 'данные', 14, BLUE, 'bold') + t(300, 180, 'условие 1', 13, ORANGE, 'bold')
b += t(360, 196, 'среднее', 13, PURPLE, 'bold') + t(430, 180, 'итог (И)', 13, GREEN, 'bold')
b += t(20, 225, 'Ответ = СУММ(I:I) — сколько строк подходят под оба условия', 14, INK, 'bold')
save('xl-task9-columns.svg', 560, 240, b)

# =========================================================================== Урок 8

def table_card(x, y, title, cols, color, rows=()):
    out = rect(x, y, 220, 30 + 24 * (len(cols) + len(rows)), '#fff', color, 2, 6)
    out += rect(x, y, 220, 30, color, color, 1, 6) + t(x + 10, y + 20, title, 14, '#fff', 'bold')
    for i, c in enumerate(cols):
        out += t(x + 12, y + 50 + i * 24, c, 13, INK, 'bold' if 'ID' in c or 'Артикул' in c else 'normal')
    return out


b = table_card(260, 20, 'Движение товаров', ['ID операции', 'Дата', 'ID магазина', 'Артикул', 'Тип операции',
                                              'Количество упаковок', 'Цена'], BLUE)
b += table_card(20, 220, 'Магазин', ['ID магазина', 'Район', 'Адрес'], GREEN)
b += table_card(520, 220, 'Товар', ['Артикул', 'Отдел', 'Наименование', 'Ед. изм.', 'Кол-во в упаковке', 'Поставщик'], ORANGE)
b += line(372, 118, 132, 262, GREEN, 2.5, 'green') + line(372, 142, 620, 262, ORANGE, 2.5, 'orange')
b += t(160, 205, 'по ID магазина', 13, GREEN, 'bold') + t(520, 205, 'по артикулу', 13, ORANGE, 'bold')
save('xl-db-schema.svg', 760, 400, b)

s = Sheet(20, 20, 4, 5, widths=[60, 70, 90, 120], name='D2', formula='=ВПР(B2;Товар!A:C;3;ЛОЖЬ)', font=13)
s.fill('A1', [['ID', 'Артикул', 'Кол-во', 'Наименование'], [1, 17, 40, 'Кефир 2,5%'], [2, 4, 12, 'Хлеб'], [3, 17, 25, 'Кефир 2,5%']])
for c in 'ABCD':
    s.set(c + '1', s.cells[c + '1'], bold=True)
s.set('D2', 'Кефир 2,5%', GREEN, True)
b = s.render(active='D2', ranges=[('D2:D4', GREEN)])
t2 = Sheet(440, 20, 3, 5, widths=[60, 80, 120], title='лист «Товар»', font=13)
t2.fill('A1', [['Артикул', 'Отдел', 'Наименование'], [4, 'Хлеб', 'Хлеб'], [11, 'Молоко', 'Молоко 3,2%'], [17, 'Молоко', 'Кефир 2,5%']])
b += t2.render(ranges=[('A4:C4', ORANGE)], marks=[('C4', GREEN)])
b += line(186, 115, 470, 159, ORANGE, 2.5, 'orange') + t(440, 205, 'ищем 17 в первом столбце «Товара»,', 13, ORANGE, 'bold')
b += t(440, 223, 'берём 3-й столбец той же строки', 13, ORANGE, 'bold')
b += t(20, 255, 'ВПР(что ищем; где ищем; номер столбца ответа; ЛОЖЬ — точное совпадение)', 14, INK, 'bold')
save('xl-vlookup.svg', 800, 270, b)

# =========================================================================== Урок 9

grid = [[3, 1, 4, 2], [2, 5, 1, 3], [6, 1, 2, 4], [1, 3, 5, 2]]
best = [[0] * 4 for _ in range(4)]
for r in range(4):
    for c in range(4):
        prev = [best[r - 1][c]] if r else []
        prev += [best[r][c - 1]] if c else []
        best[r][c] = grid[r][c] + (max(prev) if prev else 0)
s1 = Sheet(20, 30, 4, 4, widths=[46] * 4, title='Поле с монетами')
s2 = Sheet(330, 30, 4, 4, widths=[52] * 4, title='Лучшая сумма до клетки')
for r in range(4):
    for c in range(4):
        s1.set(col_name(c) + str(r + 1), grid[r][c], align='middle')
        s2.set(col_name(c) + str(r + 1), best[r][c], GREEN if (r, c) == (3, 3) else None, (r, c) == (3, 3), 'middle')
b = s1.render(ranges=[('A1:A1', ORANGE), ('D4:D4', ORANGE)])
b += s2.render(ranges=[('B2:B2', BLUE)], arrows=[('B1', 'B2', BLUE), ('A2', 'B2', BLUE)])
b += t(330, 200, 'B2 = 5 + МАКС(сверху 4; слева 5) = 10', 14, BLUE, 'bold')
b += t(20, 225, 'Робот ходит вправо и вниз. Каждая клетка: своя монета + лучший из соседей сверху и слева.', 13, INK)
save('xl-robot-dp.svg', 610, 240, b)

s1 = Sheet(20, 30, 4, 4, widths=[46] * 4, title='Стена под B1')
for r in range(4):
    for c in range(4):
        s1.set(col_name(c) + str(r + 1), grid[r][c], align='middle')
b = s1.render(thick=[('B1', 'b'), ('C2', 'r'), ('C3', 'r')], marks=[('B2', BLUE)])
b += t(260, 70, 'Через толстую линию ходить нельзя.', 14, INK, 'bold')
b += t(260, 98, 'В B2 можно прийти только слева:', 14, INK)
b += t(260, 124, '=A2+B2 (без МАКС)', 14, BLUE, 'bold', 'start', MONO)
b += t(260, 160, 'В D2 — только сверху: =D1+D2', 14, ORANGE, 'bold')
save('xl-robot-walls.svg', 600, 200, b)

# =========================================================================== Урок 10

procs = [(1, 4, []), (2, 3, []), (3, 2, [1]), (4, 5, [1, 2]), (5, 3, [3, 4])]
finish = {}
for pid, dur, deps in procs:
    finish[pid] = dur + max((finish[d] for d in deps), default=0)
b = t(20, 24, 'Диаграмма Ганта: процесс начинается, когда закончились все, от кого он зависит', 14, INK, 'bold')
scale = 40
for i, (pid, dur, deps) in enumerate(procs):
    y = 50 + i * 40
    start = finish[pid] - dur
    b += t(20, y + 20, f'Процесс {pid}', 13, INK)
    b += rect(110 + start * scale, y + 4, dur * scale, 24, ['#d0ebff', '#d3f9d8', '#fff3bf', '#e5dbff', '#ffe8cc'][i],
              [BLUE, GREEN, YELLOW, PURPLE, ORANGE][i], 2, 4)
    b += t(110 + start * scale + dur * scale / 2, y + 21, f'{dur} мс', 12, INK, 'bold', 'middle')
    for d in deps:
        j = d - 1
        b += line(110 + finish[d] * scale, 50 + j * 40 + 28, 110 + start * scale, y + 6, GRAY, 1.5, 'gray', '4 3')
for k in range(0, 13):
    x = 110 + k * scale
    b += line(x, 252, x, 258, INK, 1) + t(x, 274, k, 11, INK, 'normal', 'middle')
b += line(110, 255, 110 + 12 * scale, 255, INK, 1.5)
b += line(110 + 12 * scale, 40, 110 + 12 * scale, 250, RED, 2, None, '6 4') + t(110 + 12 * scale + 6, 60, 'всё готово: 12 мс', 13, RED, 'bold')
save('xl-gantt.svg', 780, 290, b)

s = Sheet(20, 20, 4, 6, widths=[60, 80, 90, 150], name='D5', formula='=B5+МАКС(D2;D3)', font=13)
s.fill('A1', [['ID', 'Время', 'Зависит от', 'Окончание'], [1, 4, 0, '=B2 → 4'], [2, 3, 0, '=B3 → 3'], [3, 2, 1, '=B4+D2 → 6'],
              [4, 5, '1;2', '=B5+МАКС(D2;D3) → 9'], [5, 3, '3;4', '=B6+МАКС(D4;D5) → 12']])
for c in 'ABCD':
    s.set(c + '1', s.cells[c + '1'], bold=True)
b = s.render(active='D5', arrows=[('D2', 'D5', BLUE), ('D3', 'D5', BLUE)], ranges=[('D2:D6', GREEN)])
b += callout(420, 230, 'ответ — МАКС(D2:D6) = 12', GREEN, 400, 190)
save('xl-task22-table.svg', 640, 245, b)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, content in images.items():
        (OUT / name).write_text(content, encoding='utf-8')
    print(f'Картинок: {len(images)}')


if __name__ == '__main__':
    main()
