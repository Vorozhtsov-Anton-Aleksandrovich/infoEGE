"""Источник задач курса. Генерирует:
  course/python-ege/tasks/<lesson>.json   — задачи и тесты (без решений, отдаются платформе)
  course/python-ege/solutions/<id>.py     — эталонные решения (НЕ отдаются ученикам)
Ожидаемый вывод тестов вычисляется запуском эталонного решения.

Запуск: python course-tools/build_tasks.py
"""
import json
import random
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ROOT = REPO / 'src' / 'main' / 'resources' / 'courses' / 'python-ege'
TASKS_DIR = ROOT / 'tasks'
SOL_DIR = REPO / 'course-tools' / 'solutions'

rnd = random.Random(2026)


def T(inp, hidden=True, files=None):
    """Тест: ввод (строка), скрытый ли, файлы {имя: содержимое}."""
    d = {'input': inp, 'hidden': hidden}
    if files:
        d['files'] = files
    return d


def lines(*xs):
    return '\n'.join(str(x) for x in xs) + '\n'


LESSONS = []


def lesson(slug, title):
    les = {'slug': slug, 'title': title, 'tasks': []}
    LESSONS.append(les)
    return les


def task(les, id_, title, statement, solution, tests, starter='', hint=''):
    les['tasks'].append({'id': id_, 'title': title, 'statement': statement.strip(),
                         'starterCode': starter, 'hint': hint, 'solution': solution.strip() + '\n',
                         'tests': tests})


# ---------------------------------------------------------------- Урок 1
L = lesson('01-install-idle', 'Установка Python и IDLE')
task(L, 'py01-1', 'Hello, World!',
     'Выведи строку `Hello, World!` — это традиционная первая программа.',
     "print('Hello, World!')", [T('', False)])
task(L, 'py01-2', 'Привет, имя',
     'Программа получает на вход имя. Выведи `Привет, <имя>!` — например, для `Аня` вывод `Привет, Аня!`.\n\n'
     'Подсказка: `print(\'Привет, \' + name + \'!\')`.',
     "name = input()\nprint('Привет, ' + name + '!')",
     [T('Аня\n', False), T('Максим\n'), T('Python\n')])
task(L, 'py01-3', 'Большая степень',
     'Выведи значение `2` в степени `100`. В Python целые числа могут быть любой длины.',
     'print(2 ** 100)', [T('', False)])

# ---------------------------------------------------------------- Урок 2
L = lesson('02-pycharm', 'PyCharm')
task(L, 'py02-1', 'Квадраты',
     'Дано натуральное `n` (1 ≤ n ≤ 100). Для каждого `i` от 1 до `n` выведи строку `i i²` через пробел.',
     'n = int(input())\nfor i in range(1, n + 1):\n    print(i, i * i)',
     [T('3\n', False), T('1\n'), T('10\n'), T('100\n')])
task(L, 'py02-2', 'Таблица умножения',
     'Дано число `n`. Выведи таблицу умножения на `n` от 1 до 10 в формате `n x i = результат`, '
     'например `7 x 3 = 21`.',
     "n = int(input())\nfor i in range(1, 11):\n    print(n, 'x', i, '=', n * i)",
     [T('7\n', False), T('1\n'), T('12\n'), T('0\n')])
task(L, 'py02-3', 'Найди ошибку отладчиком',
     'Программа в редакторе должна выводить сумму чисел от 1 до `n` включительно, но выдаёт неверный ответ. '
     'Найди ошибку (удобно в PyCharm: точка останова в цикле + F8) и исправь её.\n\n'
     'Например, для `n = 4` ответ `10`.',
     's = 0\nn = int(input())\nfor i in range(1, n + 1):\n    s += i\nprint(s)',
     [T('4\n', False), T('1\n'), T('100\n'), T('12345\n')],
     starter='s = 0\nn = int(input())\nfor i in range(1, n):\n    s += i\nprint(s)\n',
     hint='Какое последнее значение принимает i? Вспомни, что правая граница range не входит.')

# ---------------------------------------------------------------- Урок 3
L = lesson('03-io-variables', 'Переменные, ввод и вывод')
task(L, 'py03-1', 'Сумма двух чисел',
     'В одной строке через пробел даны два целых числа `a` и `b`. Выведи их сумму.',
     'a, b = map(int, input().split())\nprint(a + b)',
     [T('3 5\n', False), T('-7 7\n'), T('1000000000 1000000000\n'), T('0 -15\n')])
task(L, 'py03-2', 'Последняя цифра',
     'Дано натуральное число `n`. Выведи его последнюю цифру.',
     'n = int(input())\nprint(n % 10)',
     [T('2025\n', False), T('7\n'), T('1230\n'), T('99999999999999999999\n')])
task(L, 'py03-3', 'Сумма цифр трёхзначного',
     'Дано трёхзначное число. Выведи сумму его цифр. Реши через `//` и `%`, без строк.',
     'n = int(input())\na = n // 100\nb = n // 10 % 10\nc = n % 10\nprint(a + b + c)',
     [T('123\n', False), T('100\n'), T('999\n'), T('507\n')],
     hint='Сотни: n // 100. Десятки: n // 10 % 10. Единицы: n % 10.')
task(L, 'py03-4', 'Электронные часы',
     'С начала суток прошло `n` минут (`n` может быть больше 1440 — тогда прошли сутки и больше). '
     'Выведи через пробел, сколько часов и минут показывают часы (часы от 0 до 23).',
     'n = int(input())\nprint(n // 60 % 24, n % 60)',
     [T('150\n', False), T('1441\n', False), T('0\n'), T('1439\n'), T('100000\n')])
task(L, 'py03-5', 'Парты',
     'В школе открывают три класса. В каждой строке дано число учеников класса. '
     'За одной партой сидят не больше двух учеников. Сколько всего парт нужно купить?',
     'a = int(input())\nb = int(input())\nc = int(input())\nprint((a + 1) // 2 + (b + 1) // 2 + (c + 1) // 2)',
     [T('20\n21\n22\n', False), T('1\n1\n1\n'), T('0\n0\n0\n'), T('17\n19\n18\n')],
     hint='Для нечётного числа учеников нужна ещё одна парта: (x + 1) // 2.')
task(L, 'py03-6', 'Обмен значений',
     'В одной строке даны два числа `a` и `b`. Поменяй значения переменных местами и выведи `a b`.',
     'a, b = map(int, input().split())\na, b = b, a\nprint(a, b)',
     [T('1 2\n', False), T('-5 10\n'), T('7 7\n')])

# ---------------------------------------------------------------- Урок 4
L = lesson('04-conditions', 'Условия')
task(L, 'py04-1', 'Чётное или нечётное',
     'Дано целое число. Выведи `EVEN`, если оно чётное, и `ODD`, если нечётное.',
     "n = int(input())\nif n % 2 == 0:\n    print('EVEN')\nelse:\n    print('ODD')",
     [T('4\n', False), T('7\n'), T('0\n'), T('-3\n')])
task(L, 'py04-2', 'Максимум из трёх',
     'В одной строке даны три целых числа. Выведи наибольшее. Реши через `if`, без `max`.',
     'a, b, c = map(int, input().split())\nm = a\nif b > m:\n    m = b\nif c > m:\n    m = c\nprint(m)',
     [T('1 5 3\n', False), T('9 2 4\n'), T('1 2 9\n'), T('-1 -2 -3\n'), T('5 5 5\n')])
task(L, 'py04-3', 'Високосный год',
     'Год високосный, если он делится на 4, но не делится на 100, **или** делится на 400. '
     'Выведи `YES`, если год високосный, иначе `NO`.',
     "y = int(input())\nif (y % 4 == 0 and y % 100 != 0) or y % 400 == 0:\n    print('YES')\nelse:\n    print('NO')",
     [T('2024\n', False), T('1900\n', False), T('2000\n'), T('2023\n'), T('2100\n')])
task(L, 'py04-4', 'Треугольник',
     'Даны три натуральных числа — длины отрезков (в одной строке). Можно ли из них сложить невырожденный '
     'треугольник? Выведи `YES` или `NO`. Каждая сторона должна быть строго меньше суммы двух других.',
     "a, b, c = map(int, input().split())\nif a < b + c and b < a + c and c < a + b:\n    print('YES')\nelse:\n    print('NO')",
     [T('3 4 5\n', False), T('1 2 3\n', False), T('10 1 1\n'), T('5 5 5\n'), T('2 9 8\n')])
task(L, 'py04-5', 'Координатная четверть',
     'Даны координаты точки `x y` (оба не равны 0). Выведи номер четверти (1–4), в которой лежит точка.',
     'x, y = map(int, input().split())\nif x > 0 and y > 0:\n    print(1)\nelif x < 0 and y > 0:\n    print(2)\n'
     'elif x < 0 and y < 0:\n    print(3)\nelse:\n    print(4)',
     [T('1 2\n', False), T('-1 5\n'), T('-3 -3\n'), T('4 -9\n')])
task(L, 'py04-6', 'Логическая функция (задание 2)',
     'Даны значения логических переменных `x y z` (0 или 1). Вычисли '
     '`F = (x → y) ∧ (y ≡ ¬z)` и выведи `0` или `1`.',
     'x, y, z = map(int, input().split())\nF = (not x or y) and (y == (not z))\nprint(int(F))',
     [T('0 0 1\n', False), T('1 0 1\n')] + [T(f'{a} {b} {c}\n') for a in (0, 1) for b in (0, 1) for c in (0, 1)],
     hint='Импликация x → y записывается как (not x) or y.')

# ---------------------------------------------------------------- Урок 5
L = lesson('05-loops', 'Циклы')
task(L, 'py05-1', 'Сумма от 1 до n',
     'Дано натуральное `n`. Выведи сумму чисел от 1 до `n` включительно. Используй цикл.',
     'n = int(input())\ns = 0\nfor i in range(1, n + 1):\n    s += i\nprint(s)',
     [T('5\n', False), T('1\n'), T('1000\n'), T('99999\n')])
task(L, 'py05-2', 'Факториал',
     'Дано `n` (0 ≤ n ≤ 100). Выведи `n! = 1 · 2 · … · n` (по определению `0! = 1`).',
     'n = int(input())\nf = 1\nfor i in range(2, n + 1):\n    f *= i\nprint(f)',
     [T('5\n', False), T('0\n'), T('1\n'), T('20\n'), T('100\n')])
task(L, 'py05-3', 'Количество цифр',
     'Дано целое неотрицательное число. Выведи количество цифр в нём, используя `while` (без `str` и `len`).',
     'n = int(input())\ncnt = 1\nwhile n >= 10:\n    n //= 10\n    cnt += 1\nprint(cnt)',
     [T('2025\n', False), T('0\n', False), T('9\n'), T('10\n'), T('123456789012345\n')],
     hint='Аккуратно с нулём: в числе 0 одна цифра.')
task(L, 'py05-4', 'Сумма цифр',
     'Дано натуральное число (может быть очень большим). Выведи сумму его цифр, используя `while`, `%` и `//`.',
     'n = int(input())\ns = 0\nwhile n > 0:\n    s += n % 10\n    n //= 10\nprint(s)',
     [T('2025\n', False), T('7\n'), T('1000000\n'), T('99999999999999999999\n')])
task(L, 'py05-5', 'Кратные на отрезке',
     'В одной строке даны `a b k` (a ≤ b). Сколько чисел на отрезке [a; b] делятся на `k`?',
     'a, b, k = map(int, input().split())\ncnt = 0\nfor x in range(a, b + 1):\n    if x % k == 0:\n        cnt += 1\nprint(cnt)',
     [T('1 10 3\n', False), T('5 5 5\n'), T('1 100000 7\n'), T('-10 10 5\n'), T('13 17 20\n')])
task(L, 'py05-6', 'Простое число',
     'Дано натуральное `n` (n ≤ 10⁹). Выведи `YES`, если оно простое, иначе `NO`. '
     'Перебирай делители только до √n, иначе будет слишком долго.',
     "n = int(input())\nok = n > 1\nd = 2\nwhile d * d <= n:\n    if n % d == 0:\n        ok = False\n        break\n    d += 1\n"
     "print('YES' if ok else 'NO')",
     [T('7\n', False), T('1\n', False), T('2\n'), T('91\n'), T('999999937\n'), T('1000000000\n')],
     hint='while d * d <= n — это и есть «до корня».')
task(L, 'py05-7', 'Числа Фибоначчи',
     'F₁ = F₂ = 1, каждое следующее — сумма двух предыдущих: 1, 1, 2, 3, 5, 8, … Дано `n` (1 ≤ n ≤ 200). Выведи Fₙ.',
     'n = int(input())\na, b = 1, 1\nfor i in range(n - 1):\n    a, b = b, a + b\nprint(a)',
     [T('6\n', False), T('1\n'), T('2\n'), T('50\n'), T('200\n')])
task(L, 'py05-8', 'Кратные трём',
     'В первой строке `n`, далее `n` целых чисел, по одному в строке. Выведи через пробел количество чисел, '
     'кратных 3, и их сумму.',
     'n = int(input())\ncnt = 0\ns = 0\nfor i in range(n):\n    x = int(input())\n    if x % 3 == 0:\n        cnt += 1\n        s += x\nprint(cnt, s)',
     [T(lines(5, 3, 4, 9, -6, 1), False), T(lines(1, 5)), T(lines(*([30] + [rnd.randint(-1000, 1000) for _ in range(30)])))])

# ---------------------------------------------------------------- Урок 6
L = lesson('06-strings', 'Строки')
task(L, 'py06-1', 'Задом наперёд',
     'Дана строка. Выведи её задом наперёд.',
     'print(input()[::-1])',
     [T('PYTHON\n', False), T('a\n'), T('А роза упала на лапу Азора\n')])
task(L, 'py06-2', 'Палиндром',
     'Дана строка без пробелов. Выведи `YES`, если она читается одинаково слева направо и справа налево, иначе `NO`.',
     "s = input()\nprint('YES' if s == s[::-1] else 'NO')",
     [T('level\n', False), T('python\n', False), T('a\n'), T('abba\n'), T('abca\n')])
task(L, 'py06-3', 'Гласные',
     'Дана строка из латинских букв и пробелов. Посчитай количество гласных `a e i o u` (в любом регистре).',
     "s = input()\ncnt = 0\nfor c in s.lower():\n    if c in 'aeiou':\n        cnt += 1\nprint(cnt)",
     [T('Hello World\n', False), T('xyz\n'), T('AEIOU aeiou\n'), T('Programming In Python Is Fun\n')])
task(L, 'py06-4', 'Самая длинная цепочка (задание 24)',
     'Дана строка из заглавных латинских букв. Найди длину самой длинной цепочки **одинаковых** символов, '
     'стоящих подряд.',
     's = input()\ncur = best = 1\nfor i in range(1, len(s)):\n    if s[i] == s[i - 1]:\n        cur += 1\n    else:\n        cur = 1\n    best = max(best, cur)\nprint(best)',
     [T('ABBBCCD\n', False), T('A\n'), T('ABCDEF\n'), T('ZZZZZ\n'),
      T(''.join(rnd.choice('AB') for _ in range(2000)) + '\n')])
task(L, 'py06-5', 'Редактор (задание 12)',
     'Строка состоит из `n` единиц. Пока в строке есть `11`, заменяем **первое** вхождение `11` на `2`. '
     'Дано `n`, выведи получившуюся строку.',
     "n = int(input())\ns = '1' * n\nwhile '11' in s:\n    s = s.replace('11', '2', 1)\nprint(s)",
     [T('7\n', False), T('1\n'), T('2\n'), T('20\n'), T('101\n')],
     hint="s.replace('11', '2', 1) — третий аргумент 1 значит «заменить только первое».")
task(L, 'py06-6', 'Количество слов',
     'Дана строка, слова в ней разделены одним или несколькими пробелами. Выведи количество слов.',
     'print(len(input().split()))',
     [T('Я учу Python\n', False), T('one\n'), T('  много   пробелов  тут \n')])
task(L, 'py06-7', 'Без одинаковых соседей (задание 24)',
     'Дана строка из заглавных латинских букв. Найди максимальную длину подстроки, в которой **никакие два '
     'соседних символа не совпадают**.',
     's = input()\ncur = best = 1\nfor i in range(1, len(s)):\n    if s[i] != s[i - 1]:\n        cur += 1\n    else:\n        cur = 1\n    best = max(best, cur)\nprint(best)',
     [T('AABCDDE\n', False), T('A\n'), T('AAAA\n'), T('ABABAB\n'),
      T(''.join(rnd.choice('ABC') for _ in range(3000)) + '\n')])

# ---------------------------------------------------------------- Урок 7
L = lesson('07-lists', 'Списки')


def nums(k, lo=-1000, hi=1000):
    return ' '.join(str(rnd.randint(lo, hi)) for _ in range(k)) + '\n'


task(L, 'py07-1', 'Размах',
     'В одной строке через пробел даны целые числа. Выведи разность между максимальным и минимальным.',
     'a = list(map(int, input().split()))\nprint(max(a) - min(a))',
     [T('3 9 -2 5\n', False), T('7\n'), T(nums(500))])
task(L, 'py07-2', 'Пары соседей (задание 17)',
     'В одной строке даны целые числа. Найди количество пар **соседних** элементов, в которых хотя бы одно '
     'число делится на 3, и максимальную сумму элементов таких пар. Выведи два числа через пробел. '
     'Если таких пар нет, выведи `0 0`.',
     'a = list(map(int, input().split()))\ncnt = 0\nbest = None\nfor i in range(len(a) - 1):\n'
     '    if a[i] % 3 == 0 or a[i + 1] % 3 == 0:\n        cnt += 1\n        s = a[i] + a[i + 1]\n'
     '        if best is None or s > best:\n            best = s\nprint(cnt, best if best is not None else 0)',
     [T('1 3 5 7 9\n', False), T('1 2 4 5\n'), T('-3 -6 -9\n'), T(nums(1000, -10000, 10000))],
     hint='Максимум удобно начинать с None или с очень маленького числа — суммы могут быть отрицательными.')
task(L, 'py07-3', 'Больше среднего',
     'В одной строке даны целые числа. Сколько из них строго больше среднего арифметического?',
     'a = list(map(int, input().split()))\ns = sum(a)\nn = len(a)\ncnt = 0\nfor x in a:\n    if x * n > s:\n        cnt += 1\nprint(cnt)',
     [T('1 2 3 4 5\n', False), T('5 5 5\n'), T('1 1 1 100\n'), T(nums(777))],
     hint='Чтобы не связываться с дробями, сравнивай x * n > sum(a).')
task(L, 'py07-4', 'Различные числа',
     'В одной строке даны целые числа. Сколько среди них различных?',
     'print(len(set(map(int, input().split()))))',
     [T('1 2 2 3 3 3\n', False), T('7\n'), T(nums(1000, 1, 300))])
task(L, 'py07-5', 'Второй максимум',
     'В одной строке даны целые числа (различных значений хотя бы два). Выведи второе по величине **различное** значение.',
     'a = sorted(set(map(int, input().split())), reverse=True)\nprint(a[1])',
     [T('5 9 9 1 7\n', False), T('1 2\n'), T('-5 -1 -1 -3\n'), T(nums(500))])
task(L, 'py07-6', 'Циклический сдвиг',
     'В одной строке даны числа. Сдвинь список циклически на 1 вправо (последний элемент становится первым) '
     'и выведи через пробел.',
     'a = input().split()\na = [a[-1]] + a[:-1]\nprint(*a)',
     [T('1 2 3 4 5\n', False), T('7\n'), T('10 20\n')],
     hint='print(*a) печатает элементы списка через пробел.')
task(L, 'py07-7', 'Три наибольших',
     'В первой строке `n` (n ≥ 3), далее `n` целых чисел по одному в строке. Выведи три наибольших числа '
     'в порядке убывания через пробел (повторы допускаются).',
     'n = int(input())\na = [int(input()) for i in range(n)]\na.sort(reverse=True)\nprint(a[0], a[1], a[2])',
     [T(lines(5, 4, 10, 2, 8, 6), False), T(lines(3, 1, 1, 1)), T(lines(*([60] + [rnd.randint(-500, 500) for _ in range(60)])))])

# ---------------------------------------------------------------- Урок 8
L = lesson('08-functions', 'Функции')
task(L, 'py08-1', 'Простые на отрезке',
     'Напиши функцию `is_prime(n)`. В одной строке даны `a b`. Выведи количество простых чисел на отрезке [a; b].',
     'def is_prime(n):\n    if n < 2:\n        return False\n    d = 2\n    while d * d <= n:\n        if n % d == 0:\n            return False\n        d += 1\n    return True\n\n'
     'a, b = map(int, input().split())\nprint(sum(1 for x in range(a, b + 1) if is_prime(x)))',
     [T('1 10\n', False), T('1 1\n'), T('2 2\n'), T('1 10000\n'), T('100000 101000\n')],
     starter='def is_prime(n):\n    # верни True, если n простое\n    pass\n\n\na, b = map(int, input().split())\n')
task(L, 'py08-2', 'Делители (задание 25)',
     'Напиши функцию `divisors(n)`, которая возвращает отсортированный список всех делителей `n`. '
     'Дано `n` (до 10¹²). Выведи делители через пробел.',
     'def divisors(n):\n    res = []\n    d = 1\n    while d * d <= n:\n        if n % d == 0:\n            res.append(d)\n            if d != n // d:\n                res.append(n // d)\n        d += 1\n    return sorted(res)\n\n'
     'print(*divisors(int(input())))',
     [T('36\n', False), T('1\n'), T('97\n'), T('1000000\n'), T('600851475143\n')],
     hint='Перебирай d до корня и добавляй сразу пару d и n // d.')
task(L, 'py08-3', 'НОД (алгоритм Евклида)',
     'Напиши функцию `gcd(a, b)` — наибольший общий делитель по алгоритму Евклида '
     '(пока b ≠ 0: a, b = b, a % b). Даны `a b`, выведи НОД. Не используй `math.gcd`.',
     'def gcd(a, b):\n    while b != 0:\n        a, b = b, a % b\n    return a\n\na, b = map(int, input().split())\nprint(gcd(a, b))',
     [T('12 18\n', False), T('7 13\n'), T('100 10\n'), T('1071 462\n'), T('123456789012 9876543210\n')])
task(L, 'py08-4', 'Рекурсия (задание 16)',
     'Функция задана так: `F(1) = 1`, `F(n) = F(n − 1) + 2·n` при `n > 1`. Дано `n` (1 ≤ n ≤ 300). '
     'Выведи `F(n)`. Напиши именно рекурсивную функцию.',
     'def F(n):\n    if n == 1:\n        return 1\n    return F(n - 1) + 2 * n\n\nprint(F(int(input())))',
     [T('4\n', False), T('1\n'), T('10\n'), T('300\n')])
task(L, 'py08-5', 'Рекурсия с кэшем (задание 16)',
     '`F(n) = n` при `n ≤ 2`; `F(n) = F(n − 1) + 2·F(n − 2)` при `n > 2`. Дано `n` (1 ≤ n ≤ 90). Выведи `F(n)`.\n\n'
     'Без кэша рекурсия для n = 90 будет считаться годами. Используй `@lru_cache`.',
     'from functools import lru_cache\n\n@lru_cache(None)\ndef F(n):\n    if n <= 2:\n        return n\n    return F(n - 1) + 2 * F(n - 2)\n\nprint(F(int(input())))',
     [T('5\n', False), T('1\n'), T('3\n'), T('40\n'), T('90\n')],
     hint='from functools import lru_cache и строка @lru_cache(None) над def.')
task(L, 'py08-6', 'Ровно четыре делителя (задание 25)',
     'Даны `a b`. Для каждого числа на отрезке [a; b], у которого **ровно 4** делителя, выведи в отдельной '
     'строке само число и его наибольший делитель, отличный от самого числа. Числа — по возрастанию.',
     'def divisors(n):\n    res = []\n    d = 1\n    while d * d <= n:\n        if n % d == 0:\n            res.append(d)\n            if d != n // d:\n                res.append(n // d)\n        d += 1\n    return sorted(res)\n\n'
     'a, b = map(int, input().split())\nfor x in range(a, b + 1):\n    ds = divisors(x)\n    if len(ds) == 4:\n        print(x, ds[-2])',
     [T('1 20\n', False), T('5 5\n'), T('100000 100100\n'), T('1 300\n')])

# ---------------------------------------------------------------- Урок 9 (файлы)
L = lesson('09-files', 'Работа с файлами')


def f17(k):
    return lines(*[rnd.randint(-10000, 10000) for _ in range(k)])


task(L, 'py09-1', 'Файл 17.txt (задание 17)',
     'В файле `17.txt` записаны целые числа, по одному в строке. Найди количество пар соседних элементов, '
     'в которых **ровно одно** число чётное, и максимальную сумму элементов таких пар. Выведи два числа через пробел '
     '(если пар нет — `0 0`).',
     "a = [int(x) for x in open('17.txt')]\ncnt = 0\nbest = None\nfor i in range(len(a) - 1):\n"
     "    if (a[i] % 2 == 0) != (a[i + 1] % 2 == 0):\n        cnt += 1\n        s = a[i] + a[i + 1]\n"
     "        if best is None or s > best:\n            best = s\nprint(cnt, best if best is not None else 0)",
     [T('', False, {'17.txt': lines(1, 2, 4, 7, 9)}), T('', True, {'17.txt': lines(2, 4, 6)}),
      T('', True, {'17.txt': f17(2000)}), T('', True, {'17.txt': f17(5000)})],
     hint='«Ровно одно чётное» — чётности соседей различаются.')


def f24(k):
    return ''.join(rnd.choice('ABC0123456789') for _ in range(k)) + '\n'


task(L, 'py09-2', 'Файл 24.txt (задание 24)',
     'В файле `24.txt` одна строка из заглавных латинских букв и цифр. Найди длину самой длинной подстроки, '
     'состоящей **только из цифр**. Если цифр нет, выведи 0.',
     "s = open('24.txt').readline().strip()\ncur = best = 0\nfor c in s:\n    if c.isdigit():\n        cur += 1\n"
     "        best = max(best, cur)\n    else:\n        cur = 0\nprint(best)",
     [T('', False, {'24.txt': 'AB123C4567D8\n'}), T('', True, {'24.txt': 'ABCABC\n'}),
      T('', True, {'24.txt': '0123456789\n'}), T('', True, {'24.txt': f24(10000)})],
     hint='Не забудь .strip() — иначе в строку попадёт перевод строки.')


def f9(k):
    return ''.join(' '.join(str(rnd.randint(1, 60)) for _ in range(4)) + '\n' for _ in range(k))


task(L, 'py09-3', 'Файл 9.txt (задание 9)',
     'В каждой строке файла `9.txt` записаны 4 натуральных числа через пробел. Найди количество строк, в которых: '
     'все 4 числа различны **и** сумма наибольшего и наименьшего больше суммы двух оставшихся.',
     "cnt = 0\nfor line in open('9.txt'):\n    a = sorted(map(int, line.split()))\n"
     "    if len(set(a)) == 4 and a[0] + a[3] > a[1] + a[2]:\n        cnt += 1\nprint(cnt)",
     [T('', False, {'9.txt': '1 2 3 10\n5 5 1 2\n4 3 2 1\n'}), T('', True, {'9.txt': f9(100)}),
      T('', True, {'9.txt': f9(5000)})],
     hint='Отсортируй строку: тогда a[0] — минимум, a[3] — максимум.')


def f26(k, S):
    return lines(f'{S} {k}', *[rnd.randint(1, 100) for _ in range(k)])


task(L, 'py09-4', 'Файл 26.txt: архив (задание 26)',
     'В первой строке файла `26.txt` — свободный объём диска `S` и количество файлов `N`, далее `N` размеров файлов. '
     'Найди максимальное число файлов, которые можно сохранить на диск, и наибольший размер файла, который можно '
     'сохранить при этом максимальном количестве. Выведи два числа через пробел.',
     "f = open('26.txt')\nS, N = map(int, f.readline().split())\na = sorted(int(f.readline()) for i in range(N))\n"
     "k = 0\ntotal = 0\nwhile k < N and total + a[k] <= S:\n    total += a[k]\n    k += 1\n"
     "base = total - a[k - 1]\nbest = a[k - 1]\nfor j in range(k, N):\n    if base + a[j] <= S:\n        best = a[j]\n"
     "print(k, best)",
     [T('', False, {'26.txt': lines('100 4', 80, 30, 50, 40)}), T('', True, {'26.txt': lines('10 3', 3, 3, 3)}),
      T('', True, {'26.txt': f26(1000, 8000)}), T('', True, {'26.txt': f26(5000, 50000)})],
     hint='Сначала жадно бери самые маленькие. Потом попробуй заменить последний взятый файл на самый большой из возможных.')

# ---------------------------------------------------------------- Урок 10
L = lesson('10-ege-tricks', 'Приёмы Python для ЕГЭ')
task(L, 'py10-1', 'В систему счисления',
     'Даны `n` (n ≥ 0) и основание `b` (2 ≤ b ≤ 36). Выведи запись `n` в системе с основанием `b`. '
     'Цифры больше 9 — заглавные латинские буквы `A`, `B`, …',
     "n, b = map(int, input().split())\ndigits = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'\nif n == 0:\n    print(0)\nelse:\n"
     "    s = ''\n    while n > 0:\n        s = digits[n % b] + s\n        n //= b\n    print(s)",
     [T('11 2\n', False), T('255 16\n', False), T('0 7\n'), T('100 7\n'), T('123456789 36\n')])
task(L, 'py10-2', 'В десятичную',
     'Даны строка `s` — запись числа — и основание `b` через пробел. Выведи число в десятичной системе.',
     's, b = input().split()\nprint(int(s, int(b)))',
     [T('1011 2\n', False), T('FF 16\n'), T('210 3\n'), T('ZZZ 36\n')])
task(L, 'py10-3', 'Подсчёт цифр (задание 14)',
     'Даны `n`, основание `b` и цифра `d` (0 ≤ d < b). Сколько раз цифра `d` встречается в записи `n` '
     'в системе с основанием `b`? `n` может быть огромным — например, `9**100`, поэтому оно дано уже вычисленным.',
     'n, b, d = map(int, input().split())\nif n == 0:\n    print(1 if d == 0 else 0)\nelse:\n    cnt = 0\n    while n > 0:\n'
     '        if n % b == d:\n            cnt += 1\n        n //= b\n    print(cnt)',
     [T('11 2 1\n', False), T('100 7 0\n'), T(f'{9**100 + 3**50 - 7} 3 0\n'), T(f'{2**300 - 1} 2 1\n'),
      T(f'{5**80 + 25**20} 5 4\n')])
task(L, 'py10-4', 'Слова без повторов подряд (задание 8)',
     'Дана строка различных букв и число `k` через пробел. Сколько существует слов длины `k` из этих букв '
     '(буквы можно повторять), в которых никакие две **соседние** буквы не совпадают?',
     "from itertools import product\n\nletters, k = input().split()\nk = int(k)\ncnt = 0\nfor w in product(letters, repeat=k):\n"
     "    ok = True\n    for i in range(k - 1):\n        if w[i] == w[i + 1]:\n            ok = False\n            break\n"
     "    if ok:\n        cnt += 1\nprint(cnt)",
     [T('АБВ 2\n', False), T('АБВ 3\n'), T('КОТ 1\n'), T('ПРИВЕТ 5\n'), T('АБ 7\n')])
task(L, 'py10-5', 'Перестановки (задание 8)',
     'Дано слово из заглавных русских букв (до 8 букв). Сколько **различных** слов можно получить перестановкой '
     'его букв так, чтобы никакие две гласные не стояли рядом? Гласные: `АЕЁИОУЫЭЮЯ`.',
     "from itertools import permutations\n\nword = input()\nvowels = 'АЕЁИОУЫЭЮЯ'\nres = set()\nfor p in permutations(word):\n"
     "    w = ''.join(p)\n    ok = True\n    for i in range(len(w) - 1):\n        if w[i] in vowels and w[i + 1] in vowels:\n"
     "            ok = False\n    if ok:\n        res.add(w)\nprint(len(res))",
     [T('КОТ\n', False), T('МАМА\n', False), T('ЛЕТО\n'), T('АИСТ\n'), T('ПАРОХОД\n'), T('ВОДОПАДЫ\n')],
     hint='Одинаковые буквы дают одинаковые слова — собирай слова в set.')
task(L, 'py10-6', 'Таблица истинности (задание 2)',
     'Выведи все наборы `x y z w` (0/1), при которых `F = ((x → y) ∧ ¬z) ∨ w` **ложна**. '
     'Каждый набор — в отдельной строке через пробел, в порядке перебора `product([0, 1], repeat=4)`.',
     'from itertools import product\n\nfor x, y, z, w in product([0, 1], repeat=4):\n    F = ((not x or y) and not z) or w\n'
     '    if not F:\n        print(x, y, z, w)',
     [T('', False)])
task(L, 'py10-7', 'Алгоритм с двоичной записью (задание 5)',
     'Алгоритм получает натуральное `N` и строит `R`:\n\n'
     '1. Строится двоичная запись `N`.\n'
     '2. К записи справа дописывается остаток от деления суммы её цифр на 2.\n'
     '3. Шаг 2 повторяется ещё раз для новой записи.\n'
     '4. Получившаяся запись — двоичная запись `R`.\n\n'
     'Дано `M`. Выведи наименьшее `N`, для которого `R > M`.',
     "M = int(input())\nN = 1\nwhile True:\n    s = bin(N)[2:]\n    s += str(s.count('1') % 2)\n    s += str(s.count('1') % 2)\n"
     "    if int(s, 2) > M:\n        print(N)\n        break\n    N += 1",
     [T('77\n', False), T('1\n'), T('100\n'), T('1000\n'), T('123456\n')],
     hint="bin(N)[2:] — двоичная запись без '0b'; s.count('1') — сумма цифр.")
task(L, 'py10-8', 'Делимость (задание 15)',
     'Даны натуральные `p` и `q` (≤ 30). Найди **наибольшее** натуральное `A` (A ≤ 1000), при котором формула\n\n'
     '`ДЕЛ(x, A) ∨ ¬ДЕЛ(x, p) ∨ ¬ДЕЛ(x, q)`\n\n'
     'истинна для любого натурального `x` (`ДЕЛ(x, A)` — «x делится на A»). Достаточно проверять x от 1 до 1000.',
     'p, q = map(int, input().split())\nfor A in range(1000, 0, -1):\n'
     '    if all(x % A == 0 or x % p != 0 or x % q != 0 for x in range(1, 1001)):\n        print(A)\n        break',
     [T('6 4\n', False), T('2 3\n'), T('5 5\n'), T('12 18\n'), T('7 1\n')],
     hint='all(условие for x in range(1, 1001)) проверяет условие для всех x сразу.')


# ---------------------------------------------------------------- сборка
def run(code, inp, files):
    with tempfile.TemporaryDirectory() as d:
        for name, content in (files or {}).items():
            Path(d, name).write_text(content, encoding='utf-8')
        sol = Path(d, 'main.py')
        sol.write_text(code, encoding='utf-8')
        r = subprocess.run([sys.executable, '-I', str(sol)], input=inp, capture_output=True, text=True,
                           cwd=d, timeout=30, encoding='utf-8')
        if r.returncode != 0:
            raise RuntimeError(r.stderr)
        return r.stdout


def normalize(out):
    return '\n'.join(l.rstrip() for l in out.rstrip().splitlines())


def main():
    TASKS_DIR.mkdir(parents=True, exist_ok=True)
    SOL_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for les in LESSONS:
        out_tasks = []
        for t in les['tasks']:
            for test in t['tests']:
                test['output'] = normalize(run(t['solution'], test['input'], test.get('files')))
            (SOL_DIR / f"{t['id']}.py").write_text(t['solution'], encoding='utf-8')
            out_tasks.append({k: v for k, v in t.items() if k != 'solution'})
            total += 1
        data = {'lesson': les['slug'], 'tasks': out_tasks}
        (TASKS_DIR / f"{les['slug']}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Готово: {len(LESSONS)} уроков, {total} задач')


if __name__ == '__main__':
    main()
