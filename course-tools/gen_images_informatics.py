"""SVG-иллюстрации курса «Основы информатики для ЕГЭ» в courses/informatics-ege/images/.
Картинки графов к задачам рисует build_informatics.py.

Запуск: python course-tools/gen_images_informatics.py
"""
import math
from html import escape
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'courses' / 'informatics-ege' / 'images'
OUT.mkdir(parents=True, exist_ok=True)

SANS = 'Segoe UI, Arial, sans-serif'
MONO = "Consolas, 'Courier New', monospace"
INK = '#1f2328'
NAVY = '#1d3557'
RED = '#c92a2a'
BLUE = '#1c62c9'
GREEN = '#2b8a3e'
ORANGE = '#e8590c'
PURPLE = '#7048e8'
GRAY = '#868e96'
YELLOW = '#f59f00'
SOFT = {RED: '#ffe3e3', BLUE: '#d0ebff', GREEN: '#d3f9d8', ORANGE: '#ffe8cc', PURPLE: '#e5dbff', YELLOW: '#fff3bf',
        GRAY: '#f1f3f5', NAVY: '#e7f0fb'}

images = {}


def svg(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
            f'font-family="{SANS}">\n<rect width="{w}" height="{h}" fill="#ffffff"/>\n{DEFS}\n{body}\n</svg>\n')


DEFS = ('<defs>'
        + ''.join(f'<marker id="ar-{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
                  f'orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="{color}"/></marker>'
                  for name, color in [('ink', INK), ('red', RED), ('blue', BLUE), ('green', GREEN), ('gray', GRAY),
                                      ('orange', ORANGE)])
        + '</defs>')
MARK = {INK: 'ink', RED: 'red', BLUE: 'blue', GREEN: 'green', GRAY: 'gray', ORANGE: 'orange'}


def t(x, y, s, size=15, fill=INK, weight='normal', anchor='start', font=None, italic=False):
    f = f' font-family="{font}"' if font else ''
    st = ' font-style="italic"' if italic else ''
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{weight}" '
            f'text-anchor="{anchor}"{f}{st} xml:space="preserve">{escape(str(s))}</text>')


def rect(x, y, w, h, fill='#fff', stroke=GRAY, rx=8, sw=1.5, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d}/>'


def box(x, y, w, h, text, color=NAVY, size=15, font=None, fill=None, weight='bold', text_color=None):
    return (rect(x, y, w, h, fill or SOFT.get(color, '#fff'), color)
            + t(x + w / 2, y + h / 2 + size * 0.36, text, size, text_color or color, weight, 'middle', font))


def line(x1, y1, x2, y2, color=INK, sw=2, arrow=False, dash=None):
    m = f' marker-end="url(#ar-{MARK.get(color, "ink")})"' if arrow else ''
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{sw}"{m}{d}/>'


def circle(cx, cy, r, fill='#fff', stroke=INK, sw=2, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d}/>'


def lamp(cx, cy, on, scale=1.0):
    r = 26 * scale
    glow = ''
    if on:
        for i in range(8):
            a = i * math.pi / 4
            glow += line(cx + math.cos(a) * (r + 6), cy - 8 * scale + math.sin(a) * (r + 6),
                         cx + math.cos(a) * (r + 16), cy - 8 * scale + math.sin(a) * (r + 16), YELLOW, 3)
    bulb = circle(cx, cy - 8 * scale, r, '#ffe066' if on else '#e9ecef', '#f08c00' if on else GRAY, 2.5)
    base = rect(cx - 12 * scale, cy + 14 * scale, 24 * scale, 18 * scale, '#adb5bd', '#495057', 3, 1.5)
    return glow + bulb + base


def node(cx, cy, label, color=NAVY, r=18, fill='#fff', size=16, dash=None):
    return circle(cx, cy, r, fill, color, 2.2, dash) + t(cx, cy + size * 0.36, label, size, color, 'bold', 'middle')


def save(name, w, h, body):
    images[name] = svg(w, h, body)


# ====================================================================== Урок 1

b = ''
for i, (on, label, bit) in enumerate([(False, 'не горит', '0'), (True, 'горит', '1')]):
    cx = 140 + i * 240
    b += lamp(cx, 90, on)
    b += t(cx, 160, label, 16, INK, 'normal', 'middle')
    b += box(cx - 30, 172, 60, 40, bit, GREEN if on else RED, 24, MONO)
b += t(260, 98, 'или', 18, GRAY, 'normal', 'middle')
b += t(260, 248, 'Два варианта — это 1 бит информации', 16, NAVY, 'bold', 'middle')
save('bit-lamp.svg', 520, 270, b)

b = t(20, 30, '1 бит', 15, BLUE, 'bold') + t(20, 48, '2 варианта', 13, GRAY)
b += t(20, 120, '2 бита', 15, BLUE, 'bold') + t(20, 138, '4 варианта', 13, GRAY)
b += t(20, 210, '3 бита', 15, BLUE, 'bold') + t(20, 228, '8 вариантов', 13, GRAY)
root = (400, 20)
level_y = [20, 90, 180, 270]
xs = {'': 400}
for depth in range(1, 4):
    width = 600 / (2 ** depth)
    for k in range(2 ** depth):
        code = format(k, f'0{depth}b')
        xs[code] = 130 + width * (k + 0.5)
for code, x in xs.items():
    if code:
        px = xs[code[:-1]]
        py = level_y[len(code) - 1]
        y = level_y[len(code)]
        color = BLUE if code[-1] == '0' else ORANGE
        b += line(px, py + 12, x, y - 14, color, 2)
        b += t((px + x) / 2 + (-14 if code[-1] == '0' else 8), (py + y) / 2 + 2, code[-1], 13, color, 'bold')
for code, x in xs.items():
    y = level_y[len(code)]
    if len(code) == 3:
        b += box(x - 30, y - 14, 60, 30, code, GREEN, 15, MONO)
    else:
        b += circle(x, y, 9, '#fff', NAVY, 2)
b += t(400, 320, 'Каждый новый бит удваивает число вариантов: 2 → 4 → 8', 15, NAVY, 'bold', 'middle')
save('choice-tree.svg', 760, 340, b)

steps = ['бит', 'байт', 'Кбайт', 'Мбайт', 'Гбайт']
mult = ['× 8', '× 1024', '× 1024', '× 1024']
b = ''
for i, s in enumerate(steps):
    x = 30 + i * 150
    y = 230 - i * 45
    b += rect(x, y, 120, 46, SOFT[BLUE] if i else SOFT[GREEN], BLUE if i else GREEN, 6)
    b += t(x + 60, y + 30, s, 18, NAVY, 'bold', 'middle')
    if i < 4:
        b += line(x + 120, y + 14, x + 150, y - 16, ORANGE, 2.5, True)
        b += t(x + 128, y - 14, mult[i], 14, ORANGE, 'bold')
b += line(690, 60, 690, 250, RED, 2.5, True) + t(700, 160, 'вниз: умножаем', 14, RED, 'bold')
b += line(20, 250, 20, 90, GREEN, 2.5, True) + t(28, 80, 'вверх: делим', 14, GREEN, 'bold')
b += t(400, 310, '1 байт = 8 бит      1 Кбайт = 1024 байт = 2¹⁰ байт      1 Кбайт = 2¹³ бит', 15, NAVY, 'bold', 'middle')
save('units-ladder.svg', 820, 330, b)

# ====================================================================== Урок 2

b = ''
# ладони: 10 пальцев
for hand in range(2):
    ox = 40 + hand * 170
    b += rect(ox, 120, 120, 90, '#ffe8cc', '#d9480f', 30, 2)
    for f in range(5):
        fx = ox + 8 + f * 22
        h = [50, 70, 78, 70, 55][f] if hand == 0 else [55, 70, 78, 70, 50][f]
        b += rect(fx, 130 - h, 18, h + 20, '#ffe8cc', '#d9480f', 9, 2)
        b += t(fx + 9, 125 - h + 18, str(hand * 5 + f), 12, '#d9480f', 'bold', 'middle')
b += t(190, 245, 'Человек: 10 пальцев → 10 цифр (0…9)', 15, NAVY, 'bold', 'middle')
b += t(190, 268, 'десятичная система', 14, GRAY, 'normal', 'middle')
b += line(400, 40, 400, 270, '#dee2e6', 2)
b += lamp(500, 120, False) + lamp(640, 120, True)
b += t(500, 190, '0', 22, RED, 'bold', 'middle', MONO) + t(640, 190, '1', 22, GREEN, 'bold', 'middle', MONO)
b += t(570, 245, 'Компьютер: 2 состояния → 2 цифры', 15, NAVY, 'bold', 'middle')
b += t(570, 268, 'двоичная система', 14, GRAY, 'normal', 'middle')
save('fingers-lamps.svg', 760, 290, b)


def weights(ox, oy, digits, base, color):
    out = ''
    n = len(digits)
    for i, d in enumerate(digits):
        x = ox + i * 90
        p = n - 1 - i
        w = base ** p
        out += t(x + 35, oy, f'{base}{"⁰¹²³⁴⁵⁶⁷"[p]} = {w}', 13, GRAY, 'normal', 'middle')
        out += box(x, oy + 10, 70, 56, d, color, 30, MONO)
        out += t(x + 35, oy + 92, f'{d}·{w}', 15, color, 'bold', 'middle')
    return out


b = t(20, 30, 'Десятичная система: веса 1, 10, 100', 15, NAVY, 'bold')
b += weights(60, 60, '345', 10, BLUE)
b += t(340, 115, '= 300 + 40 + 5 = 345', 18, BLUE, 'bold')
b += t(20, 200, 'Двоичная система: веса 1, 2, 4, 8', 15, NAVY, 'bold')
b += weights(60, 230, '1011', 2, ORANGE)
b += t(430, 285, '= 8 + 0 + 2 + 1 = 11', 18, ORANGE, 'bold')
b += t(20, 360, 'Вес разряда растёт справа налево: каждый следующий в q раз больше.', 14, GRAY)
save('place-values.svg', 760, 380, b)

b = t(20, 30, 'Переводим 25 в двоичную систему: делим на 2', 16, NAVY, 'bold')
n = 25
rows = []
while n:
    rows.append((n, n // 2, n % 2))
    n //= 2
colors = [RED, BLUE, GREEN, ORANGE, PURPLE]
for i, (a, q, r) in enumerate(rows):
    y = 70 + i * 46
    b += t(60, y, f'{a} : 2 = {q}', 20, INK, 'normal', 'start', MONO)
    b += t(250, y, 'остаток', 15, GRAY)
    b += box(320, y - 26, 44, 36, str(r), colors[i], 22, MONO)
b += line(400, 70 + 4 * 46, 400, 52, RED, 3, True)
b += t(415, 140, 'читаем', 15, RED, 'bold') + t(415, 160, 'снизу', 15, RED, 'bold') + t(415, 180, 'вверх', 15, RED, 'bold')
for i, (a, q, r) in enumerate(reversed(rows)):
    b += box(520 + i * 48, 110, 42, 46, str(r), colors[len(rows) - 1 - i], 26, MONO)
b += t(640, 190, '25 = 11001₂', 20, NAVY, 'bold', 'middle')
b += t(640, 215, 'проверка: 16 + 8 + 1 = 25', 14, GRAY, 'normal', 'middle')
save('division-column.svg', 790, 320, b)

b = t(20, 30, '1011010110₂: группы справа налево', 16, NAVY, 'bold')


def groups(oy, bits, size, base, title):
    out = t(20, oy, title, 15, NAVY, 'bold')
    pad = (-len(bits)) % size
    full = '0' * pad + bits
    cols = [BLUE, GREEN, YELLOW, PURPLE]
    x = 20
    digits = ''
    for g in range(len(full) // size):
        chunk = full[g * size:(g + 1) * size]
        c = cols[g % 4]
        w = size * 26 + 12
        out += rect(x, oy + 12, w, 46, SOFT[c], c, 8)
        for j, ch in enumerate(chunk):
            is_pad = g * size + j < pad
            out += t(x + 18 + j * 26, oy + 43, ch, 22, GRAY if is_pad else INK, 'bold', 'middle', MONO)
        d = '0123456789ABCDEF'[int(chunk, 2)]
        digits += d
        out += t(x + w / 2, oy + 86, d, 24, c if c != YELLOW else '#a35f00', 'bold', 'middle', MONO)
        x += w + 12
    out += t(x + 20, oy + 43, f'→ {digits}{"₈" if base == 8 else "₁₆"}', 22, NAVY, 'bold', 'start', MONO)
    return out


b += groups(70, '1011010110', 3, 8, 'Триады (по 3 бита) — восьмеричная система')
b += groups(200, '1011010110', 4, 16, 'Тетрады (по 4 бита) — шестнадцатеричная система')
b += t(20, 330, 'Серые нули дописаны слева, чтобы получились полные группы.', 14, GRAY)
save('bit-groups.svg', 640, 350, b)

b = t(20, 30, '1011₂ + 111₂ столбиком', 16, NAVY, 'bold')
cols_x = [140, 180, 220, 260, 300]
for i, ch in enumerate(' 111 '):
    if ch.strip():
        b += t(cols_x[i], 70, ch, 15, RED, 'bold', 'middle', MONO)
b += t(cols_x[0] - 60, 70, 'в уме:', 13, RED)
for i, ch in enumerate(' 1011'):
    b += t(cols_x[i], 105, ch, 26, INK, 'normal', 'middle', MONO)
b += t(100, 140, '+', 26, INK, 'normal', 'middle', MONO)
for i, ch in enumerate('  111'):
    b += t(cols_x[i], 140, ch, 26, INK, 'normal', 'middle', MONO)
b += line(120, 155, 320, 155, INK, 2)
for i, ch in enumerate('10010'):
    b += t(cols_x[i], 192, ch, 26, GREEN, 'bold', 'middle', MONO)
b += t(370, 80, '1 + 1 = 10₂: пишем 0, 1 в уме', 15, NAVY)
b += t(370, 110, '1 + 1 + 1 = 11₂: пишем 1, 1 в уме', 15, NAVY)
b += t(370, 150, 'проверка: 11 + 7 = 18', 15, GRAY)
b += t(370, 180, '10010₂ = 16 + 2 = 18 ✓', 15, GREEN, 'bold')
save('binary-addition.svg', 680, 220, b)

# ====================================================================== Урок 3


def wire_scene(kind):
    out = ''
    # батарейка слева, лампа справа, провод сверху
    out += rect(30, 120, 30, 60, '#ffd43b', '#f08c00', 4) + t(45, 200, 'батарея', 12, GRAY, 'normal', 'middle')
    on = kind in ('not-off', 'and', 'or')
    out += lamp(470, 115, on, 0.9)
    if kind.startswith('not'):
        pressed = kind == 'not-on'
        out += line(45, 120, 45, 60) + line(45, 60, 210, 60)
        out += line(290, 60, 470, 60) + line(470, 60, 470, 85)
        # кнопка-инвертор: размыкает цепь при нажатии
        out += rect(210, 40, 80, 40, SOFT[RED] if pressed else SOFT[GREEN], RED if pressed else GREEN, 8)
        out += t(250, 66, 'НЕ', 16, RED if pressed else GREEN, 'bold', 'middle')
        out += t(250, 105, 'A = 1 (нажата)' if pressed else 'A = 0 (не нажата)', 13, INK, 'normal', 'middle')
        out += t(470, 200, 'горит: ¬A = 1' if not pressed else 'не горит: ¬A = 0', 14, GREEN if not pressed else RED,
                 'bold', 'middle')
    elif kind == 'and':
        out += line(45, 120, 45, 60) + line(45, 60, 130, 60)
        out += box(130, 40, 70, 40, 'A', GREEN, 18) + line(200, 60, 270, 60)
        out += box(270, 40, 70, 40, 'B', GREEN, 18) + line(340, 60, 470, 60) + line(470, 60, 470, 85)
        out += t(235, 115, 'кнопки подряд: ток идёт, только если нажаты обе', 13, INK, 'normal', 'middle')
        out += t(470, 200, 'A ∧ B = 1', 15, GREEN, 'bold', 'middle')
    else:
        out += line(45, 120, 45, 60) + line(45, 60, 140, 60) + line(140, 30, 140, 100)
        out += line(140, 30, 200, 30) + box(200, 12, 70, 36, 'A', GREEN, 18) + line(270, 30, 330, 30)
        out += line(140, 100, 200, 100) + box(200, 82, 70, 36, 'B', GRAY, 18) + line(270, 100, 330, 100)
        out += line(330, 30, 330, 100) + line(330, 60, 470, 60) + line(470, 60, 470, 85)
        out += t(235, 150, 'кнопки рядом: хватит любой', 13, INK, 'normal', 'middle')
        out += t(470, 200, 'A ∨ B = 1', 15, GREEN, 'bold', 'middle')
    out += line(45, 180, 45, 230) + line(45, 230, 470, 230) + line(470, 230, 470, 160)
    return out


save('switch-not.svg', 1060, 250, wire_scene('not-off') + f'<g transform="translate(530 0)">{wire_scene("not-on")}</g>')
save('switch-and.svg', 540, 250, wire_scene('and'))
save('switch-or.svg', 540, 250, wire_scene('or'))

b = t(20, 30, 'Обещание: «Если сдам ЕГЭ, то куплю торт»', 17, NAVY, 'bold')
cases = [('0', '0', 'не сдал, торта нет', '1', 'честно'), ('0', '1', 'не сдал, но торт купил', '1', 'не запрещалось'),
         ('1', '0', 'сдал, а торта нет', '0', 'обман!'), ('1', '1', 'сдал и купил торт', '1', 'честно')]
for i, (a, bb, text, res, verdict) in enumerate(cases):
    y = 60 + i * 62
    bad = res == '0'
    c = RED if bad else GREEN
    b += rect(20, y, 720, 52, SOFT[c], c, 8)
    b += t(40, y + 33, f'ЕГЭ = {a}', 17, INK, 'bold', 'start', MONO) + t(170, y + 33, f'торт = {bb}', 17, INK, 'bold', 'start', MONO)
    b += t(320, y + 33, text, 16, INK)
    b += t(580, y + 33, f'{a} → {bb} = {res}', 17, c, 'bold', 'start', MONO)
    b += t(730, y + 20, verdict, 13, c, 'bold', 'end')
save('implication-promise.svg', 760, 320, b)

b = t(300, 30, 'Приоритет: сверху — выполняется раньше', 16, NAVY, 'bold', 'middle')
levels = [('( )', 'скобки', PURPLE), ('¬', 'НЕ', RED), ('∧', 'И', BLUE), ('∨', 'ИЛИ', GREEN), ('→', 'импликация', ORANGE),
          ('≡', 'эквивалентность', GRAY)]
for i, (sym, name, c) in enumerate(levels):
    w = 120 + i * 80
    x = 300 - w / 2
    y = 50 + i * 48
    b += rect(x, y, w, 42, SOFT[c], c, 6)
    b += t(300, y + 28, f'{sym}  {name}', 17, c if c != GRAY else INK, 'bold', 'middle')
save('priority-pyramid.svg', 600, 350, b)

b = t(20, 30, 'Строки таблицы истинности — это двоичный счёт', 16, NAVY, 'bold')
b += t(60, 62, 'x', 18, BLUE, 'bold', 'middle') + t(100, 62, 'y', 18, BLUE, 'bold', 'middle') + t(140, 62, 'z', 18, BLUE, 'bold', 'middle')
for k in range(8):
    y = 92 + k * 30
    bits = format(k, '03b')
    b += rect(36, y - 21, 128, 28, SOFT[BLUE] if k % 2 == 0 else '#fff', '#dee2e6', 4, 1)
    for j, ch in enumerate(bits):
        b += t(60 + j * 40, y, ch, 18, INK, 'normal', 'middle', MONO)
    b += line(180, y - 6, 220, y - 6, GRAY, 1.5, True)
    b += t(235, y, f'{k}', 18, ORANGE, 'bold', 'start', MONO)
b += t(300, 120, 'Последний столбец: 0 1 0 1 0 1 0 1', 15, INK)
b += t(300, 150, 'Средний:            0 0 1 1 0 0 1 1', 15, INK)
b += t(300, 180, 'Первый:              0 0 0 0 1 1 1 1', 15, INK)
b += t(300, 230, '3 переменные → 2³ = 8 строк', 16, NAVY, 'bold')
b += t(300, 258, '(числа от 0 до 7 в двоичной записи)', 14, GRAY)
save('binary-count.svg', 640, 340, b)

# ====================================================================== Урок 4

b = t(20, 30, 'Задание 2: подписать столбцы буквами', 16, NAVY, 'bold')
heads = ['?', '?', '?', '?', 'F']
data = [['', '1', '0', '1', '0'], ['1', '0', '1', '0', '0'], ['1', '0', '', '1', '0']]
for j, h in enumerate(heads):
    b += box(40 + j * 60, 50, 56, 40, h, ORANGE if h == '?' else NAVY, 20, MONO)
for i, row in enumerate(data):
    for j, v in enumerate(row):
        b += rect(40 + j * 60, 96 + i * 42, 56, 38, SOFT[YELLOW] if v == '' else '#fff', '#adb5bd', 4, 1)
        b += t(68 + j * 60, 122 + i * 42, v, 18, INK, 'normal', 'middle', MONO)
b += line(360, 140, 420, 140, GRAY, 2.5, True)
for j, h in enumerate(['w', 'x', 'z', 'y', 'F']):
    b += box(440 + j * 60, 50, 56, 40, h, GREEN if j < 4 else NAVY, 20, MONO)
b += t(440, 130, 'Пропуски (жёлтые) заполняем', 14, INK) + t(440, 150, 'как угодно, но строки', 14, INK)
b += t(440, 170, 'не должны совпасть.', 14, INK)
b += t(440, 215, 'Ответ: wxzy', 18, GREEN, 'bold')
save('task2-idea.svg', 760, 240, b)

# ====================================================================== Урок 5


def number_line(segs, red=None, width=700, lo=0, hi=70, title=None):
    out = ''
    pad = 40

    def X(v):
        return pad + (v - lo) / (hi - lo) * (width - 2 * pad)
    y0 = 40 if not title else 60
    if title:
        out += t(20, 28, title, 16, NAVY, 'bold')
    axis = y0 + len(segs) * 36 + 20
    if red:
        for a, bb in red:
            out += f'<rect x="{X(a)}" y="{y0 - 14}" width="{X(bb) - X(a)}" height="{axis - y0 + 14}" fill="#ffc9c9" opacity="0.8"/>'
    out += line(pad - 20, axis, width - pad + 20, axis, INK, 1.5, True)
    for v in range(lo, hi + 1, 10):
        out += line(X(v), axis - 5, X(v), axis + 5, INK, 1.5) + t(X(v), axis + 22, v, 13, INK, 'normal', 'middle')
    for i, (name, a, bb, c) in enumerate(segs):
        y = y0 + i * 36
        out += line(X(a), y, X(bb), y, c, 7)
        out += circle(X(a), y, 5, c, c, 1) + circle(X(bb), y, 5, c, c, 1)
        out += t(X(bb) + 12, y + 5, f'{name} = [{a}; {bb}]', 15, c, 'bold')
    return out, axis


body, _ = number_line([('P', 10, 40, BLUE), ('Q', 20, 60, GREEN)])
save('segments-pq.svg', 700, 150, body)
body, axis = number_line([('P', 10, 40, BLUE), ('Q', 20, 60, GREEN), ('A', 10, 20, ORANGE)], red=[(10, 20)],
                         title='Красная зона: x ∈ P, но x ∉ Q — её закрывает A')
save('segments-method.svg', 700, axis + 40, body)

b = t(20, 28, 'Кратные 6 и кратные 4: общие — кратные 12 = НОК(6, 4)', 16, NAVY, 'bold')
for v in range(1, 37):
    x = 20 + (v - 1) * 24
    m6, m4 = v % 6 == 0, v % 4 == 0
    fill = '#ffc9c9' if (m6 and m4) else SOFT[BLUE] if m6 else SOFT[GREEN] if m4 else '#fff'
    stroke = RED if (m6 and m4) else BLUE if m6 else GREEN if m4 else '#dee2e6'
    b += rect(x, 50, 22, 34, fill, stroke, 3, 1.5)
    b += t(x + 11, 72, v, 11, INK, 'bold' if (m6 or m4) else 'normal', 'middle')
b += rect(20, 110, 18, 18, SOFT[BLUE], BLUE, 3) + t(46, 124, 'делится на 6', 14, BLUE)
b += rect(190, 110, 18, 18, SOFT[GREEN], GREEN, 3) + t(216, 124, 'делится на 4', 14, GREEN)
b += rect(360, 110, 18, 18, '#ffc9c9', RED, 3) + t(386, 124, 'делится и на 6, и на 4: 12, 24, 36', 14, RED, 'bold')
save('multiples.svg', 900, 150, b)

# ====================================================================== Урок 6

morse = [('Е', '·', 'часто'), ('Т', '—', 'часто'), ('А', '· —', 'часто'), ('О', '— — —', 'часто'),
         ('Щ', '— — · —', 'редко'), ('Ю', '· · — —', 'редко'), ('Ъ', '— — · — — ·', 'редко')]
b = t(20, 28, 'Азбука Морзе: чем чаще буква, тем короче код', 16, NAVY, 'bold')
for i, (letter, code, freq) in enumerate(morse):
    y = 52 + i * 38
    c = GREEN if freq == 'часто' else ORANGE
    b += box(20, y, 44, 30, letter, c, 18)
    b += t(84, y + 22, code, 20, INK, 'bold', 'start', MONO)
    b += t(380, y + 21, freq, 14, c, 'bold')
save('morse.svg', 480, 330, b)

b = t(20, 28, 'Код: А = 0, Б = 01, В = 10. Как прочитать 010?', 16, NAVY, 'bold')
for k, (parts, letters) in enumerate([(['0', '10'], ['А', 'В']), (['01', '0'], ['Б', 'А'])]):
    oy = 60 + k * 100
    x = 40
    cols = [BLUE, ORANGE]
    for j, (p, l) in enumerate(zip(parts, letters)):
        w = len(p) * 34 + 10
        b += rect(x, oy, w, 40, SOFT[cols[j]], cols[j], 6)
        b += t(x + w / 2, oy + 28, p, 22, INK, 'bold', 'middle', MONO)
        b += t(x + w / 2, oy + 66, l, 18, cols[j], 'bold', 'middle')
        x += w + 6
    b += t(220, oy + 28, f'→ {"".join(letters)}', 20, NAVY, 'bold')
b += t(380, 120, 'Две разные расшифровки!', 16, RED, 'bold')
b += t(380, 145, 'Причина: код А = 0 —', 14, INK) + t(380, 165, 'начало кода Б = 01.', 14, INK)
save('ambiguity.svg', 640, 250, b)


def tree(codes, bad=(), free=(), width=560, depth=3, title=None, label_codes=True):
    out = ''
    top = 50 if title else 30
    if title:
        out += t(20, 26, title, 15, NAVY, 'bold')
    level = 70

    def pos(p):
        d = len(p)
        idx = int(p, 2) if p else 0
        return (idx + 0.5) / 2 ** d * width, top + d * level
    nodes = {''}
    for c in list(codes.values()) + list(free):
        for i in range(1, len(c) + 1):
            nodes.add(c[:i])
    for p in sorted(nodes, key=len):
        if not p:
            continue
        (x1, y1), (x2, y2) = pos(p[:-1]), pos(p)
        is_free = any(p.startswith(f) for f in free)
        c = GREEN if is_free else INK
        out += line(x1, y1 + 10, x2, y2 - 14, c, 2, dash='6 4' if is_free else None)
        out += t((x1 + x2) / 2 + (-14 if p[-1] == '0' else 8), (y1 + y2) / 2 + 2, p[-1], 13, GRAY, 'bold')
    letter_at = {v: k for k, v in codes.items()}
    for p in nodes:
        x, y = pos(p)
        if p in letter_at:
            c = RED if letter_at[p] in bad else BLUE
            out += circle(x, y, 17, SOFT[c], c, 2.5) + t(x, y + 6, letter_at[p], 16, c, 'bold', 'middle')
            if label_codes:
                out += t(x, y + 34, p, 13, INK, 'normal', 'middle', MONO)
        elif p in free:
            out += circle(x, y, 14, SOFT[GREEN], GREEN, 2, '4 3') + t(x, y + 32, p, 13, GREEN, 'bold', 'middle', MONO)
        else:
            out += circle(x, y, 8, '#fff', INK, 2)
    return out, top + depth * level + 50


body, h = tree({'А': '0', 'Б': '10', 'В': '110', 'Г': '111'}, title='Влево — 0, вправо — 1. Все буквы в листьях: Фано ✓')
save('fano-tree.svg', 560, h, body)
body, h = tree({'А': '0', 'Б': '01', 'В': '11'}, bad=('А', 'Б'), depth=2,
               title='А = 0 стоит над Б = 01: код А — начало кода Б ✗')
save('fano-tree-bad.svg', 560, h, body)
body, h = tree({'А': '00', 'Б': '01', 'В': '100'}, free=('11', '101'),
               title='А = 00, Б = 01, В = 100. Свободные ветки: 11 и 101')
body += t(380, h - 18, 'кратчайший код для новой буквы — 11', 14, GREEN, 'bold', 'middle')
save('fano-free.svg', 560, h + 10, body)

# ====================================================================== Урок 7

b = t(20, 28, 'Алфавит из 36 символов: между какими ступеньками?', 16, NAVY, 'bold')
for i, k in enumerate(range(3, 8)):
    x = 30 + i * 120
    y = 240 - i * 40
    hit = k == 6
    c = GREEN if hit else BLUE
    b += rect(x, y, 110, 40, SOFT[c], c, 4)
    b += t(x + 55, y + 26, f'2{"⁰¹²³⁴⁵⁶⁷"[k]} = {2 ** k}', 16, c, 'bold', 'middle')
b += circle(360 + 30, 190, 22, SOFT[ORANGE], ORANGE, 2.5) + t(390, 196, '36', 16, ORANGE, 'bold', 'middle')
b += line(390, 168, 410, 150, ORANGE, 2, True)
b += t(430, 150, '32 < 36 ≤ 64', 15, INK, 'bold')
b += t(430, 172, 'берём верхнюю ступеньку:', 14, INK)
b += t(430, 194, '6 бит на символ', 15, GREEN, 'bold')
save('alphabet-ladder.svg', 660, 300, b)

b = t(20, 28, '60 бит раскладываем по байтам-коробкам (по 8 бит)', 16, NAVY, 'bold')
bits_left = 60
for i in range(8):
    x = 20 + i * 92
    fill = min(8, bits_left)
    bits_left -= fill
    partial = fill < 8
    b += rect(x, 60, 84, 70, SOFT[ORANGE] if partial else SOFT[BLUE], ORANGE if partial else BLUE, 6, 2)
    for j in range(8):
        cx = x + 12 + (j % 4) * 20
        cy = 82 + (j // 4) * 26
        b += circle(cx, cy, 7, BLUE if j < fill else '#fff', BLUE if j < fill else '#ced4da', 1.5)
    b += t(x + 42, 150, f'байт {i + 1}', 13, ORANGE if partial else INK, 'bold' if partial else 'normal', 'middle')
b += t(20, 190, '7 байт = 56 бит — мало. Последние 4 бита занимают отдельную коробку.', 15, INK)
b += t(20, 215, 'Пароль занимает 8 байт: 60 / 8 = 7,5 → округляем вверх.', 15, ORANGE, 'bold')
save('byte-boxes.svg', 760, 235, b)

# ====================================================================== Урок 8

smile = ['00111100', '01000010', '10100101', '10000001', '10100101', '10011001', '01000010', '00111100']
b = t(20, 28, 'Растр 8×8: каждый квадрат — пиксель', 16, NAVY, 'bold')
for r, row in enumerate(smile):
    for c, v in enumerate(row):
        b += f'<rect x="{30 + c * 30}" y="{50 + r * 30}" width="30" height="30" fill="{"#f59f00" if v == "1" else "#fff"}" stroke="#adb5bd"/>'
b += line(290, 170, 340, 170, GRAY, 2.5, True)
for r, row in enumerate(smile):
    b += t(360, 72 + r * 30, row, 18, INK, 'normal', 'start', MONO)
b += t(360, 310, '64 пикселя × 1 бит = 64 бита = 8 байт', 14, NAVY, 'bold')
save('raster.svg', 640, 330, b)

b = t(20, 28, 'Больше цветов — больше бит на пиксель', 16, NAVY, 'bold')
grad = [[(r * 8 + c) for c in range(8)] for r in range(8)]
for k, (colors, bits) in enumerate([(2, 1), (16, 4), (256, 8)]):
    ox = 30 + k * 230
    for r in range(8):
        for c in range(8):
            v = (r + c) / 14
            q = round(v * (colors - 1)) / (colors - 1)
            g = int(255 - q * 200)
            col = f'#{g:02x}{int(g * 0.85 + 30):02x}{255:02x}' if colors > 2 else ('#1c62c9' if q > 0.5 else '#ffffff')
            b += f'<rect x="{ox + c * 22}" y="{50 + r * 22}" width="22" height="22" fill="{col}" stroke="#e9ecef" stroke-width="0.5"/>'
    b += t(ox + 88, 250, f'{colors} цветов', 15, INK, 'bold', 'middle')
    b += t(ox + 88, 272, f'{bits} бит на пиксель', 14, BLUE, 'bold', 'middle')
save('palette-depth.svg', 720, 290, b)

b = t(20, 28, 'Звук: волну измеряют через равные промежутки времени', 16, NAVY, 'bold')
W, H0 = 640, 170
b += line(40, H0, 680, H0, GRAY, 1.5, True) + t(684, H0 + 18, 'время', 13, GRAY, 'normal', 'end')
b += line(40, 290, 40, 50, GRAY, 1.5, True)
for lvl in range(-4, 5):
    y = H0 - lvl * 26
    b += line(40, y, 660, y, '#f1f3f5', 1)
pts = []
for i in range(0, 621, 4):
    x = 40 + i
    y = H0 - 95 * math.sin(i / 620 * 4 * math.pi) * (0.7 + 0.3 * math.cos(i / 300))
    pts.append(f'{x:.1f},{y:.1f}')
b += f'<polyline points="{" ".join(pts)}" fill="none" stroke="{BLUE}" stroke-width="2.5"/>'
for i in range(0, 621, 40):
    x = 40 + i
    y = H0 - 95 * math.sin(i / 620 * 4 * math.pi) * (0.7 + 0.3 * math.cos(i / 300))
    q = round((H0 - y) / 26)
    yq = H0 - q * 26
    b += line(x, H0, x, yq, ORANGE, 2) + circle(x, yq, 5, ORANGE, ORANGE, 1)
b += t(60, 315, 'Оранжевые точки — измерения. Их число в секунду — частота дискретизации (Гц).', 14, ORANGE, 'bold')
b += t(60, 338, 'Серые линии — уровни громкости. 2^разрядность уровней (16 бит → 65 536).', 14, GRAY, 'bold')
save('sound-wave.svg', 720, 355, b)

b = t(20, 28, 'Передача файла по каналу связи', 16, NAVY, 'bold')
b += rect(30, 60, 140, 100, SOFT[BLUE], BLUE, 8) + t(100, 105, 'файл', 18, BLUE, 'bold', 'middle')
b += t(100, 130, 'V бит', 16, BLUE, 'normal', 'middle')
b += rect(190, 95, 300, 30, SOFT[GRAY], GRAY, 15)
for i in range(9):
    b += t(215 + i * 30, 116, '10'[i % 2], 16, GREEN, 'bold', 'middle', MONO)
b += line(480, 110, 520, 110, GREEN, 3, True)
b += rect(530, 60, 140, 100, SOFT[GREEN], GREEN, 8) + t(600, 115, 'получатель', 16, GREEN, 'bold', 'middle')
b += t(340, 160, 'скорость — бит в секунду', 14, GRAY, 'normal', 'middle')
b += t(340, 205, 'время = V / скорость', 20, NAVY, 'bold', 'middle')
save('channel.svg', 700, 230, b)

# ====================================================================== Урок 9

b = t(20, 28, 'Карта → граф: оставляем только города и связи', 16, NAVY, 'bold')
cities = {'А': (60, 140), 'Б': (170, 70), 'В': (190, 210), 'Г': (300, 110)}
roads = [('А', 'Б'), ('А', 'В'), ('Б', 'Г'), ('В', 'Г'), ('Б', 'В')]
b += rect(20, 45, 330, 210, '#e6f4ea', '#b7dfc0', 12)
b += '<path d="M 120 50 C 140 120, 90 180, 140 255" stroke="#74c0fc" stroke-width="10" fill="none" opacity="0.7"/>'
for a, c in roads:
    (x1, y1), (x2, y2) = cities[a], cities[c]
    b += f'<path d="M {x1} {y1} Q {(x1 + x2) / 2 + 20} {(y1 + y2) / 2 - 20} {x2} {y2}" stroke="#868e96" stroke-width="5" fill="none"/>'
for name, (x, y) in cities.items():
    b += rect(x - 14, y - 22, 28, 22, '#ffe8cc', ORANGE, 3) + f'<path d="M {x - 16} {y - 22} L {x} {y - 34} L {x + 16} {y - 22} z" fill="{RED}"/>'
    b += t(x, y + 18, name, 15, INK, 'bold', 'middle')
b += line(365, 150, 415, 150, GRAY, 3, True)
for a, c in roads:
    (x1, y1), (x2, y2) = cities[a], cities[c]
    b += line(x1 + 400, y1, x2 + 400, y2, INK, 2.5)
for name, (x, y) in cities.items():
    b += node(x + 400, y, name)
b += t(560, 280, 'вершины — города, рёбра — дороги', 14, GRAY, 'normal', 'middle')
save('city-graph.svg', 760, 295, b)

b = t(20, 28, 'Дорога А–В на схеме и в таблице (записана дважды)', 16, NAVY, 'bold')
gn = {'А': (60, 160), 'Б': (160, 70), 'В': (160, 250), 'Г': (280, 160)}
ge = [('А', 'Б', 7), ('А', 'В', 5), ('Б', 'Г', 9), ('В', 'Г', 4), ('Б', 'В', 3)]
for a, c, w in ge:
    (x1, y1), (x2, y2) = gn[a], gn[c]
    hl = {a, c} == {'А', 'В'}
    b += line(x1, y1, x2, y2, ORANGE if hl else GRAY, 5 if hl else 2.5)
    b += t((x1 + x2) / 2 + 10, (y1 + y2) / 2 - 6, w, 15, ORANGE if hl else INK, 'bold')
for v, (x, y) in gn.items():
    b += node(x, y, v, ORANGE if v in 'АВ' else NAVY)
order = list(gn)
for i, r in enumerate([''] + order):
    for j, c in enumerate([''] + order):
        x, y = 360 + j * 52, 60 + i * 46
        w = next((ww for a, cc, ww in ge if {a, cc} == {r, c}), None)
        hl = {r, c} == {'А', 'В'}
        head = i == 0 or j == 0
        b += rect(x, y, 50, 44, SOFT[ORANGE] if hl else (SOFT[GRAY] if head else '#fff'), ORANGE if hl else '#ced4da', 0, 2 if hl else 1)
        b += t(x + 25, y + 28, (r if j == 0 else c) if head else (w or ''), 16, ORANGE if hl else INK, 'bold' if head or hl else 'normal', 'middle')
b += t(360, 310, 'Степень вершины = заполненные клетки в строке', 14, NAVY, 'bold')
save('graph-table.svg', 640, 325, b)

b = t(20, 28, 'Число путей в вершину = сумма чисел у вершин, откуда ведут стрелки', 16, NAVY, 'bold')
pn = {'А': (50, 160), 'Б': (180, 70), 'В': (180, 250), 'Г': (320, 160), 'Д': (460, 160)}
pe = [('А', 'Б'), ('А', 'В'), ('Б', 'Г'), ('В', 'Г'), ('Б', 'Д'), ('Г', 'Д'), ('В', 'Д')]
counts = {'А': 1, 'Б': 1, 'В': 1, 'Г': 2, 'Д': 4}
for a, c in pe:
    (x1, y1), (x2, y2) = pn[a], pn[c]
    d = math.hypot(x2 - x1, y2 - y1)
    b += line(x1 + (x2 - x1) / d * 20, y1 + (y2 - y1) / d * 20, x2 - (x2 - x1) / d * 24, y2 - (y2 - y1) / d * 24, GRAY, 2.2, True)
for v, (x, y) in pn.items():
    b += node(x, y, v, GREEN if v == 'Д' else NAVY)
    b += box(x + 18, y - 46, 34, 26, str(counts[v]), GREEN, 16)
b += t(20, 300, 'Г = Б + В = 1 + 1 = 2;   Д = Б + Г + В = 1 + 2 + 1 = 4', 15, INK, 'bold')
save('paths-count.svg', 560, 320, b)

# ====================================================================== Урок 10

b = t(20, 28, 'IP-адрес похож на почтовый адрес', 16, NAVY, 'bold')
for i, (part, color, label) in enumerate([('192', BLUE, ''), ('168', BLUE, ''), ('37', BLUE, ''), ('200', ORANGE, '')]):
    x = 40 + i * 120
    b += box(x, 60, 100, 50, part, color, 24, MONO)
    if i < 3:
        b += t(x + 110, 95, '.', 26, INK, 'bold', 'middle')
b += rect(40, 125, 340, 8, BLUE, BLUE, 4) + t(210, 160, 'адрес сети — «улица»', 15, BLUE, 'bold', 'middle')
b += rect(400, 125, 100, 8, ORANGE, ORANGE, 4) + t(450, 160, 'узел — «дом»', 15, ORANGE, 'bold', 'middle')
for k in range(4):
    x = 90 + k * 100
    b += rect(x, 200, 60, 50, '#fff3bf', '#f08c00', 3) + f'<path d="M {x - 6} 200 L {x + 30} 176 L {x + 66} 200 z" fill="{RED}"/>'
    b += t(x + 30, 232, str(198 + k), 14, INK, 'bold', 'middle')
b += rect(60, 256, 420, 22, '#dee2e6', '#adb5bd', 4) + t(270, 272, 'улица 192.168.37 — у всех домов общая', 13, INK, 'normal', 'middle')
b += t(270, 305, 'Каждое число — 1 байт (0…255), весь адрес — 4 байта = 32 бита', 14, GRAY, 'normal', 'middle')
save('ip-postal.svg', 560, 320, b)

b = t(20, 28, 'IP 192.168.37.200 и маска 255.255.240.0 (20 единиц)', 16, NAVY, 'bold')
ip = [192, 168, 37, 200]
mask = [255, 255, 240, 0]
net = [a & m for a, m in zip(ip, mask)]
rows_ip = [('IP', ip), ('маска', mask), ('сеть = IP & маска', net)]
for r, (name, bytes_) in enumerate(rows_ip):
    y = 80 + r * 50
    b += t(20, y, name, 15, INK, 'bold')
    for bi, byte in enumerate(bytes_):
        bits = format(byte, '08b')
        for j, ch in enumerate(bits):
            pos = bi * 8 + j
            x = 190 + pos * 17 + bi * 10
            b += t(x, y, ch, 16, BLUE if pos < 20 else ORANGE, 'bold' if pos < 20 else 'normal', 'middle', MONO)
    b += t(780, y, '.'.join(map(str, bytes_)), 15, GREEN if r == 2 else INK, 'bold', 'start', MONO)
bx = 190 + 19.5 * 17 + 2 * 10 + 3
b += line(bx, 52, bx, 200, RED, 2.5, dash='6 4')
b += t(bx - 8, 222, 'сеть (единицы маски)', 13, BLUE, 'bold', 'end') + t(bx + 8, 222, 'узел (нули маски)', 13, ORANGE, 'bold')
b += line(180, 140, 760, 140, INK, 1.5)
save('ip-mask.svg', 920, 240, b)

for name, content in images.items():
    (OUT / name).write_text(content, encoding='utf-8')
print(f'Картинок: {len(images)}')
