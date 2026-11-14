"""Генерирует SVG-иллюстрации курса в course/python-ege/images/.
Запуск: python course-tools/gen_images.py
"""
from pathlib import Path
from html import escape

OUT = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'courses' / 'python-ege' / 'images'
OUT.mkdir(parents=True, exist_ok=True)

SANS = "Segoe UI, Arial, sans-serif"
MONO = "Consolas, 'Courier New', monospace"
ACCENT = '#e5484d'
BLUE = '#2f6fdb'
GREEN = '#2f9e44'


def svg(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
            f'font-family="{SANS}">\n<rect width="{w}" height="{h}" fill="#ffffff"/>\n{body}\n</svg>\n')


def t(x, y, s, size=14, fill='#222', weight='normal', font=None, anchor='start'):
    f = f' font-family="{font}"' if font else ''
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{weight}" '
            f'text-anchor="{anchor}"{f} xml:space="preserve">{escape(s)}</text>')


def window(x, y, w, h, title, dark=False):
    bg = '#2b2d30' if dark else '#ffffff'
    bar = '#1e1f22' if dark else '#e9ecef'
    tc = '#dfe1e5' if dark else '#333'
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{bg}" stroke="#adb5bd"/>'
            f'<path d="M{x} {y+8} a8 8 0 0 1 8 -8 h{w-16} a8 8 0 0 1 8 8 v22 h-{w} z" fill="{bar}"/>'
            + t(x + 12, y + 20, title, 13, tc)
            + t(x + w - 70, y + 20, '—   ☐   ✕', 12, tc))


def callout(x, y, text, tx, ty, color=ACCENT, w=None):
    w = w or (len(text) * 7.6 + 20)
    return (f'<line x1="{x}" y1="{y}" x2="{tx}" y2="{ty}" stroke="{color}" stroke-width="2" marker-end="url(#arr)"/>'
            f'<rect x="{x - 4}" y="{y - 18}" width="{w}" height="26" rx="6" fill="{color}"/>'
            + t(x + 6, y, text, 13, '#fff', 'bold'))


ARROW_DEF = (f'<defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
             f'orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="{ACCENT}"/></marker>'
             f'<marker id="arrb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
             f'orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="#333"/></marker></defs>')


def box(x, y, w, h, text, fill='#f1f3f5', stroke='#868e96', size=14, font=None, color='#222', rx=8):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>'
            + t(x + w / 2, y + h / 2 + size * 0.35, text, size, color, font=font, anchor='middle'))


def line(x1, y1, x2, y2, label=None):
    s = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#333" stroke-width="1.8" marker-end="url(#arrb)"/>'
    if label:
        s += t((x1 + x2) / 2 + 6, (y1 + y2) / 2 - 4, label, 13, '#333', 'bold')
    return s


images = {}

# 1. Установщик Python
b = ARROW_DEF + window(20, 20, 680, 360, 'Python 3.13.0 (64-bit) Setup')
b += t(60, 85, 'Install Python 3.13.0 (64-bit)', 22, '#1c3d6e', 'bold')
b += t(60, 112, 'Select Install Now to install Python with default settings,', 13, '#444')
b += t(60, 130, 'or choose Customize to enable or disable features.', 13, '#444')
b += f'<rect x="60" y="150" width="380" height="54" rx="4" fill="#f8f9fa" stroke="#ced4da"/>'
b += t(80, 174, '→ Install Now', 15, '#1c3d6e', 'bold')
b += t(95, 193, 'C:\\Users\\Имя\\AppData\\Local\\Programs\\Python', 11, '#666')
b += f'<rect x="60" y="214" width="380" height="40" rx="4" fill="#f8f9fa" stroke="#ced4da"/>'
b += t(80, 239, '→ Customize installation', 15, '#1c3d6e', 'bold')
b += f'<rect x="60" y="292" width="16" height="16" fill="#fff" stroke="#555"/>' + t(64, 305, '✓', 13, '#333')
b += t(86, 305, 'Use admin privileges when installing py.exe', 13)
b += f'<rect x="54" y="318" width="300" height="30" rx="4" fill="none" stroke="{ACCENT}" stroke-width="3"/>'
b += f'<rect x="60" y="325" width="16" height="16" fill="#fff" stroke="#555"/>' + t(63, 338, '✓', 14, ACCENT, 'bold')
b += t(86, 338, 'Add python.exe to PATH', 13, '#222', 'bold')
b += callout(470, 300, 'Обязательно поставь галочку!', 356, 333, w=230)
b += callout(470, 180, 'Потом жми Install Now', 442, 178, color=BLUE, w=180).replace('url(#arr)', 'url(#arrb)')
images['python-installer.svg'] = svg(720, 400, b)

# 2. IDLE Shell
b = window(20, 20, 680, 300, 'IDLE Shell 3.13.0')
b += t(32, 66, 'File  Edit  Shell  Debug  Options  Window  Help', 13, '#333')
b += '<line x1="20" y1="76" x2="700" y2="76" stroke="#dee2e6"/>'
rows = [('Python 3.13.0 (tags/v3.13.0) on win32', '#333'),
        ('Type "help", "copyright", "credits" or "license()" for more information.', '#333'),
        ('>>> 2 + 3 * 4', '#7b2cbf'), ('14', '#1c3d6e'), ('>>> 17 // 5', '#7b2cbf'), ('3', '#1c3d6e'),
        ('>>> 17 % 5', '#7b2cbf'), ('2', '#1c3d6e'), ('>>> |', '#7b2cbf')]
for i, (s, c) in enumerate(rows):
    b += t(36, 102 + i * 22, s, 14, c, font=MONO)
b += ARROW_DEF + callout(440, 170, 'Пишешь после >>> и жмёшь Enter', 140, 146, w=245)
b += callout(440, 230, 'Результат появляется сразу', 60, 191, color=BLUE, w=220).replace('url(#arr)', 'url(#arrb)')
images['idle-shell.svg'] = svg(720, 340, b)

# 3. IDLE Editor + Run menu
b = ARROW_DEF + window(20, 20, 680, 300, 'hello.py - C:/Users/Имя/ege/hello.py (3.13.0)')
b += ''.join(t(x, 66, m, 13, '#333') for x, m in [(32, 'File'), (70, 'Edit'), (108, 'Format'), (170, 'Run'), (210, 'Options'), (272, 'Window'), (330, 'Help')])
b += f'<rect x="164" y="52" width="36" height="20" fill="none" stroke="{ACCENT}" stroke-width="2" rx="3"/>'
b += '<line x1="20" y1="76" x2="700" y2="76" stroke="#dee2e6"/>'
code = [("name", "#222"), ("print", "#222")]
b += t(36, 104, "name = ", 15, '#222', font=MONO) + t(99, 104, "input", 15, '#7b2cbf', font=MONO) + t(144, 104, "()", 15, '#222', font=MONO)
b += t(36, 128, "print", 15, '#7b2cbf', font=MONO) + t(81, 128, "(", 15, '#222', font=MONO) + t(90, 128, "'Привет,'", 15, GREEN, font=MONO) + t(173, 128, ", name)", 15, '#222', font=MONO)
# dropdown
b += '<rect x="168" y="76" width="230" height="110" fill="#fff" stroke="#999"/>'
items = ['Run Module          F5', 'Run... Customized  Shift+F5', 'Check Module     Alt+X', 'Python Shell']
for i, s in enumerate(items):
    if i == 0:
        b += f'<rect x="169" y="{80 + i*26}" width="228" height="24" fill="#d0e2ff"/>'
    b += t(180, 97 + i * 26, s, 13, '#222', 'bold' if i == 0 else 'normal')
b += callout(440, 140, 'Run → Run Module (или F5)', 398, 92, w=215)
b += t(36, 290, 'Перед запуском IDLE попросит сохранить файл (Ctrl+S)', 13, BLUE, 'bold')
images['idle-editor.svg'] = svg(720, 340, b)

# 4. PyCharm New Project
b = ARROW_DEF + window(20, 20, 720, 380, 'New Project', dark=True)
b += '<rect x="20" y="50" width="170" height="350" fill="#1e1f22"/>'
for i, s in enumerate(['Pure Python', 'Django', 'Flask', 'Jupyter', '…']):
    if i == 0:
        b += f'<rect x="28" y="{62+i*30}" width="154" height="26" rx="4" fill="#2e436e"/>'
    b += t(40, 80 + i * 30, s, 13, '#dfe1e5')
fields = [('Name:', 'ege'), ('Location:', 'C:\\Users\\Имя\\PycharmProjects'),
          ('Interpreter type:', '● Project venv    ○ Base conda    ○ Custom'),
          ('Python version:', 'Python 3.13  ▾')]
for i, (k, v) in enumerate(fields):
    y = 90 + i * 48
    b += t(210, y, k, 13, '#a9b7c6')
    if i in (0, 1, 3):
        b += f'<rect x="340" y="{y-18}" width="300" height="26" rx="4" fill="#1e1f22" stroke="#4e5157"/>'
    b += t(350, y, v, 13, '#dfe1e5')
b += f'<rect x="210" y="{90+3*48-24}" width="440" height="36" rx="5" fill="none" stroke="{ACCENT}" stroke-width="2.5"/>'
b += f'<rect x="210" y="270" width="14" height="14" fill="#1e1f22" stroke="#999"/>' + t(232, 282, 'Create Git repository', 13, '#dfe1e5')
b += f'<rect x="210" y="296" width="14" height="14" fill="#1e1f22" stroke="#999"/>' + t(232, 308, 'Create a welcome script', 13, '#dfe1e5')
b += f'<rect x="610" y="352" width="110" height="32" rx="5" fill="#3574f0"/>' + t(665, 373, 'Create', 14, '#fff', 'bold', anchor='middle')
b += callout(430, 340, 'Тут должна быть твоя версия Python', 430, 252, w=270)
images['pycharm-new-project.svg'] = svg(760, 420, b)

# 5. PyCharm Run
b = ARROW_DEF + window(20, 20, 720, 400, 'ege – task5.py', dark=True)
b += '<rect x="20" y="50" width="720" height="34" fill="#2b2d30" stroke="#1e1f22"/>'
b += t(36, 72, '▣ ege  ▾    main  ▾', 13, '#dfe1e5')
b += t(520, 72, 'task5  ▾', 13, '#dfe1e5')
b += f'<polygon points="600,60 600,78 614,69" fill="{GREEN}"/>' + f'<circle cx="637" cy="69" r="7" fill="#8ec07c"/>'
b += f'<rect x="592" y="55" width="28" height="28" rx="5" fill="none" stroke="{ACCENT}" stroke-width="2"/>'
b += '<rect x="20" y="84" width="160" height="196" fill="#2b2d30" stroke="#1e1f22"/>'
b += t(30, 104, 'Project ▾', 12, '#a9b7c6') + t(36, 126, '▾ ege', 13, '#dfe1e5') + t(56, 148, 'task5.py', 13, '#dfe1e5')
code = ['for n in range(1, 6):', '    print(n, n * n)']
for i, s in enumerate(code):
    b += t(196, 110 + i * 22, str(i + 1), 13, '#6f737a', font=MONO) + t(222, 110 + i * 22, s, 14, '#dfe1e5', font=MONO)
b += '<rect x="20" y="280" width="720" height="140" fill="#1e1f22"/>'
b += t(32, 300, 'Run:  task5  ×', 12, '#a9b7c6')
b += f'<rect x="28" y="310" width="14" height="14" fill="{ACCENT}"/>'
out = ['C:\\Users\\Имя\\ege\\.venv\\Scripts\\python.exe task5.py', '1 1', '2 4', '3 9', '4 16  …']
for i, s in enumerate(out):
    b += t(56, 322 + i * 19, s, 13, '#a9b7c6' if i == 0 else '#dfe1e5', font=MONO)
b += callout(420, 200, 'Запуск ▶ (Shift+F10)', 600, 86, w=170)
b += callout(380, 250, 'Окно Run: вывод и ввод программы', 300, 340, color=BLUE, w=260).replace('url(#arr)', 'url(#arrb)')
b += t(60, 413, '■ — остановить программу', 12, '#ffb4b4')
images['pycharm-run.svg'] = svg(760, 440, b)

# 6. PyCharm Debug
b = ARROW_DEF + window(20, 20, 720, 380, 'ege – task5.py [Debug]', dark=True)
code = ['s = 0', 'for n in range(1, 6):', '    s += n * n', 'print(s)']
for i, s in enumerate(code):
    y = 80 + i * 24
    if i == 2:
        b += f'<rect x="60" y="{y-16}" width="680" height="22" fill="#2d4a7a"/>'
        b += f'<circle cx="48" cy="{y-5}" r="7" fill="{ACCENT}"/>'
    b += t(64, y, str(i + 1), 13, '#6f737a', font=MONO) + t(90, y, s, 14, '#dfe1e5', font=MONO)
b += '<rect x="20" y="200" width="720" height="200" fill="#1e1f22"/>'
b += t(32, 222, 'Debug: task5   Threads & Variables   Console', 12, '#a9b7c6')
b += t(40, 248, '▶ F9   ⤼ F8   ↓ F7', 13, '#8ec07c', font=MONO)
vars_ = [('s', '5', 'int'), ('n', '3', 'int')]
for i, (k, v, ty) in enumerate(vars_):
    y = 280 + i * 26
    b += t(60, y, f'{k} = {{{ty}}} {v}', 14, '#dfe1e5', font=MONO)
b += callout(80, 185, 'Точка останова: клик слева от строки', 46, 132, w=285)
b += callout(420, 300, 'Текущие значения переменных', 200, 290, color=BLUE, w=230).replace('url(#arr)', 'url(#arrb)')
b += callout(420, 250, 'F8 — выполнить одну строку', 185, 245, w=215)
images['pycharm-debug.svg'] = svg(760, 420, b)

# 7. Переменные
b = ARROW_DEF
for i, (name, val) in enumerate([('a', '50'), ('b', '7'), ('name', "'Аня'")]):
    x = 40 + i * 220
    b += f'<path d="M{x} 80 l20 -20 h140 l-20 20 z" fill="#ffe8a3" stroke="#c99a2e"/>'
    b += f'<rect x="{x}" y="80" width="140" height="90" fill="#ffd166" stroke="#c99a2e"/>'
    b += t(x + 70, 135, val, 26, '#333', 'bold', MONO, 'middle')
    b += f'<rect x="{x+30}" y="185" width="80" height="28" rx="5" fill="#fff" stroke="#333"/>'
    b += t(x + 70, 205, name, 15, '#333', 'bold', MONO, 'middle')
b += t(40, 250, 'a = a * 10  → в коробку a кладём новое значение, старое (5) пропадает', 15, '#333')
images['variables.svg'] = svg(700, 270, b)

# 8. if-flow
b = ARROW_DEF
b += box(250, 20, 200, 40, 'n = int(input())', font=MONO)
b += line(350, 60, 350, 90)
b += f'<polygon points="350,90 450,130 350,170 250,130" fill="#d0ebff" stroke="{BLUE}" stroke-width="1.5"/>' + t(350, 135, 'n > 0 ?', 14, '#222', font=MONO, anchor='middle')
b += line(450, 130, 540, 130, 'да') + box(540, 110, 150, 40, "'положительное'", '#d3f9d8', GREEN, 13, MONO)
b += line(350, 170, 350, 200, 'нет')
b += f'<polygon points="350,200 450,240 350,280 250,240" fill="#d0ebff" stroke="{BLUE}" stroke-width="1.5"/>' + t(350, 245, 'n < 0 ?', 14, '#222', font=MONO, anchor='middle')
b += line(450, 240, 540, 240, 'да') + box(540, 220, 150, 40, "'отрицательное'", '#d3f9d8', GREEN, 13, MONO)
b += line(350, 280, 350, 310, 'нет') + box(275, 310, 150, 40, "'ноль'", '#d3f9d8', GREEN, 13, MONO)
b += t(20, 135, 'if', 16, BLUE, 'bold', MONO) + t(20, 245, 'elif', 16, BLUE, 'bold', MONO) + t(20, 335, 'else', 16, BLUE, 'bold', MONO)
images['if-flow.svg'] = svg(710, 370, b)

# 9. range
b = ARROW_DEF + t(20, 36, 'range(2, 7)', 18, BLUE, 'bold', MONO)
for i in range(9):
    x = 30 + i * 70
    inside = 2 <= i < 7
    b += box(x, 60, 56, 50, str(i), '#d3f9d8' if inside else '#f1f3f5', GREEN if inside else '#ced4da', 20, MONO,
             '#222' if inside else '#adb5bd')
b += t(30 + 2 * 70 + 28, 135, 'начало (входит)', 13, GREEN, 'bold', anchor='middle')
b += t(30 + 7 * 70 + 28, 135, 'конец (НЕ входит)', 13, ACCENT, 'bold', anchor='middle')
b += f'<line x1="{30+7*70+28}" y1="118" x2="{30+7*70+28}" y2="112" stroke="{ACCENT}"/>'
b += t(20, 175, 'for i in range(2, 7):  →  i = 2, 3, 4, 5, 6   (5 шагов = 7 − 2)', 15, '#333', font=MONO)
b += t(20, 205, 'range(1, n + 1)  →  от 1 до n включительно', 15, '#333', font=MONO)
images['range.svg'] = svg(680, 225, b)

# 10. while flow
b = ARROW_DEF
b += box(220, 20, 240, 40, 'n = 2025, s = 0', font=MONO)
b += line(340, 60, 340, 90)
b += f'<polygon points="340,90 440,130 340,170 240,130" fill="#d0ebff" stroke="{BLUE}" stroke-width="1.5"/>' + t(340, 135, 'n > 0 ?', 14, '#222', font=MONO, anchor='middle')
b += line(340, 170, 340, 200, 'да')
b += box(240, 200, 200, 64, '', '#d3f9d8', GREEN)
b += t(340, 226, 's += n % 10', 14, '#222', font=MONO, anchor='middle') + t(340, 250, 'n //= 10', 14, '#222', font=MONO, anchor='middle')
b += '<path d="M240 232 H170 V130 H236" fill="none" stroke="#333" stroke-width="1.8" marker-end="url(#arrb)"/>'
b += t(80, 200, 'повтор', 13, '#333', 'bold')
b += line(440, 130, 530, 130, 'нет') + box(530, 110, 130, 40, 'print(s)', font=MONO)
b += t(20, 300, 'n: 2025 → 202 → 20 → 2 → 0 (стоп)    s: 5 → 7 → 7 → 9', 14, '#333', font=MONO)
images['while-flow.svg'] = svg(680, 320, b)

# 11. string index
s = 'PYTHON'
b = ARROW_DEF + t(20, 30, "s = 'PYTHON'", 18, BLUE, 'bold', MONO)
for i, c in enumerate(s):
    x = 60 + i * 80
    hl = 1 <= i < 4
    b += box(x, 70, 70, 60, c, '#ffe8a3' if hl else '#f1f3f5', '#c99a2e' if hl else '#868e96', 26, MONO)
    b += t(x + 35, 60, str(i), 15, GREEN, 'bold', MONO, 'middle')
    b += t(x + 35, 152, str(i - len(s)), 15, ACCENT, 'bold', MONO, 'middle')
b += t(20, 60, 'i', 14, GREEN, 'bold') + t(10, 152, '-i', 14, ACCENT, 'bold')
b += f'<path d="M140 165 v10 h230 v-10" fill="none" stroke="#c99a2e" stroke-width="2"/>'
b += t(255, 195, "s[1:4] = 'YTH'", 16, '#8a6a00', 'bold', MONO, 'middle')
b += t(20, 230, "s[0] = 'P'    s[-1] = 'N'    s[:3] = 'PYT'    s[::-1] = 'NOHTYP'", 15, '#333', font=MONO)
images['string-index.svg'] = svg(660, 250, b)

# 12. list index
vals = [5, 100, 2, 9]
b = t(20, 30, 'a = [5, 100, 2, 9]', 18, BLUE, 'bold', MONO)
for i, v in enumerate(vals):
    x = 60 + i * 110
    b += box(x, 70, 100, 60, str(v), '#d0ebff', BLUE, 24, MONO)
    b += t(x + 50, 60, f'a[{i}]', 14, GREEN, 'bold', MONO, 'middle')
    b += t(x + 50, 152, f'a[{i - 4}]', 14, ACCENT, 'bold', MONO, 'middle')
b += t(20, 190, 'len(a) = 4, последний индекс = len(a) − 1 = 3', 15, '#333', font=MONO)
b += t(20, 215, 'a[4] → IndexError: list index out of range', 15, ACCENT, font=MONO)
images['list-index.svg'] = svg(540, 235, b)

# 13. function
b = ARROW_DEF
b += box(20, 80, 120, 50, 'x = 5', '#ffe8a3', '#c99a2e', 18, MONO)
b += line(140, 105, 200, 105, '')
b += f'<rect x="200" y="40" width="280" height="130" rx="12" fill="#e7f5ff" stroke="{BLUE}" stroke-width="2"/>'
b += t(216, 70, 'def square(x):', 16, BLUE, 'bold', MONO)
b += t(240, 110, 'return x * x', 16, '#222', font=MONO)
b += t(216, 155, 'тело функции', 13, '#555')
b += line(480, 105, 540, 105) + box(540, 80, 120, 50, '25', '#d3f9d8', GREEN, 22, MONO)
b += t(20, 160, 'аргумент', 13, '#555') + t(545, 160, 'результат', 13, '#555')
b += t(20, 215, 'print(square(5))  # 25', 16, '#333', font=MONO)
images['function.svg'] = svg(680, 235, b)

# 14. file read
b = ARROW_DEF
b += f'<rect x="20" y="20" width="400" height="200" rx="10" fill="#fff9db" stroke="#c99a2e" stroke-width="2"/>'
b += t(36, 48, 'Папка C:\\Users\\Имя\\ege', 15, '#8a6a00', 'bold')
b += t(56, 90, 'task17.py', 15, '#222', font=MONO)
b += t(56, 125, '17.txt', 15, '#222', font=MONO)
b += t(56, 175, 'в одной папке ✔', 15, GREEN, 'bold')
b += f'<rect x="460" y="20" width="250" height="200" rx="8" fill="#f8f9fa" stroke="#adb5bd"/>'
b += t(476, 46, '17.txt', 13, '#555', 'bold')
for i, v in enumerate(['1534', '-87', '9002', '45', '…']):
    b += t(480, 76 + i * 26, v, 15, '#222', font=MONO)
b += '<line x1="200" y1="120" x2="455" y2="100" stroke="#333" stroke-width="1.8" marker-end="url(#arrb)"/>'
b += t(20, 260, "a = [int(x) for x in open('17.txt')]   # [1534, -87, 9002, 45, ...]", 15, '#333', font=MONO)
images['file-read.svg'] = svg(730, 280, b)

# 15. base convert
b = ARROW_DEF + t(20, 30, '11 → двоичная система', 18, BLUE, 'bold')
steps = [(11, 5, 1), (5, 2, 1), (2, 1, 0), (1, 0, 1)]
for i, (n, q, r) in enumerate(steps):
    y = 65 + i * 40
    b += t(40, y, f'{n:>2} // 2 = {q}', 16, '#222', font=MONO)
    b += t(200, y, f'остаток {n} % 2 =', 16, '#555', font=MONO)
    b += f'<rect x="370" y="{y-20}" width="30" height="28" rx="5" fill="#ffe8a3" stroke="#c99a2e"/>' + t(385, y, str(r), 17, '#222', 'bold', MONO, 'middle')
b += '<path d="M420 190 C 470 150, 470 90, 420 50" fill="none" stroke="#e5484d" stroke-width="2.5" marker-end="url(#arr)"/>'
b += t(470, 120, 'читаем снизу вверх', 14, ACCENT, 'bold')
b += t(20, 240, "11₁₀ = 1011₂        int('1011', 2) = 11", 17, '#333', 'bold', MONO)
images['base-convert.svg'] = svg(680, 260, b)

for name, content in images.items():
    (OUT / name).write_text(content, encoding='utf-8')
print(f'Сгенерировано {len(images)} картинок в {OUT}')
