// Интерактивные виджеты в теории уроков: <div class="widget" data-widget="имя" data-параметр="…">.
// Ничего не отправляют на сервер и не оцениваются — их можно «потрогать». Список имён — CourseMarkdown.WIDGETS.
// CSP запрещает style-атрибуты в разметке, поэтому оформление только классами (стили в app.css).
(function () {
    'use strict';

    const SVG_NS = 'http://www.w3.org/2000/svg';

    // ================= Общие помощники =================

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    function svg(tag, attrs, text) {
        const node = document.createElementNS(SVG_NS, tag);
        Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, attrs[k]); });
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function append(parent) {
        for (let i = 1; i < arguments.length; i++) {
            const child = arguments[i];
            if (child === null || child === undefined) {
                continue;
            }
            parent.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
        }
        return parent;
    }

    function span(cls, text) {
        return el('span', cls, text);
    }

    function input(value, attrs) {
        const node = el('input');
        node.value = value === undefined ? '' : value;
        Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, attrs[k]); });
        return node;
    }

    function numberInput(value, min, max) {
        const attrs = { type: 'number', inputmode: 'numeric' };
        if (min !== undefined) {
            attrs.min = min;
        }
        if (max !== undefined) {
            attrs.max = max;
        }
        return input(value, attrs);
    }

    function select(options, value) {
        const node = el('select');
        options.forEach(function (o) {
            const opt = el('option', null, o[1]);
            opt.value = o[0];
            node.appendChild(opt);
        });
        node.value = value;
        return node;
    }

    function field(label, control) {
        const l = el('label', 'w-field');
        append(l, span('w-label', label), control);
        return l;
    }

    function button(text, cls) {
        const b = el('button', cls || 'w-button', text);
        b.type = 'button';
        return b;
    }

    function frame(root, title) {
        root.replaceChildren();
        root.classList.add('widget-box');
        root.appendChild(el('div', 'widget-title', '🛠 ' + title));
        return root;
    }

    function sup(n) {
        const digits = '⁰¹²³⁴⁵⁶⁷⁸⁹';
        return String(n).split('').map(function (c) { return c === '-' ? '⁻' : digits[+c]; }).join('');
    }

    function sub(n) {
        const digits = '₀₁₂₃₄₅₆₇₈₉';
        return String(n).split('').map(function (c) { return digits[+c]; }).join('');
    }

    function fmt(n) {
        if (typeof n === 'bigint') {
            return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        }
        if (!isFinite(n)) {
            return '∞';
        }
        const rounded = Math.round(n * 1e6) / 1e6;
        return rounded.toLocaleString('ru-RU', { maximumFractionDigits: 6 });
    }

    function onInput(controls, fn) {
        controls.forEach(function (c) {
            c.addEventListener('input', fn);
            c.addEventListener('change', fn);
        });
        fn();
    }

    function ceilLog2(n) {
        let i = 0;
        while (Math.pow(2, i) < n) {
            i++;
        }
        return i;
    }

    // n = 2^k · m, m нечётное — для приёма «раскладывай на степени двойки».
    function powerSplit(n) {
        let k = 0;
        let m = n;
        while (m > 0 && m % 2 === 0) {
            m /= 2;
            k++;
        }
        return { k: k, m: m };
    }

    function powerText(n) {
        const p = powerSplit(n);
        if (p.k === 0) {
            return String(n);
        }
        return p.m === 1 ? '2' + sup(p.k) : p.m + '·2' + sup(p.k);
    }

    function message(text, cls) {
        return el('div', 'w-msg ' + (cls || ''), text);
    }

    // ================= Логические выражения =================
    // Приоритет (от слабого к сильному): ≡ ⊕, →, ∨, ∧, ¬. Одинаковые операции — слева направо.

    const OPS = {
        not: { sym: '¬', prec: 5 },
        and: { sym: '∧', prec: 4 },
        or: { sym: '∨', prec: 3 },
        imp: { sym: '→', prec: 2 },
        eq: { sym: '≡', prec: 1 },
        xor: { sym: '⊕', prec: 1 }
    };

    const WORD_OPS = { not: 'not', 'не': 'not', and: 'and', 'и': 'and', or: 'or', 'или': 'or', xor: 'xor' };

    function tokenize(text) {
        const tokens = [];
        let i = 0;
        const symbols = [
            ['<->', 'eq'], ['<=>', 'eq'], ['->', 'imp'], ['=>', 'imp'], ['==', 'eq'], ['!=', 'xor'], ['&&', 'and'],
            ['||', 'or'], ['/\\', 'and'], ['\\/', 'or'], ['¬', 'not'], ['!', 'not'], ['~', 'not'], ['∧', 'and'],
            ['&', 'and'], ['∨', 'or'], ['|', 'or'], ['→', 'imp'], ['≡', 'eq'], ['↔', 'eq'], ['=', 'eq'],
            ['⊕', 'xor'], ['^', 'xor'], ['≠', 'xor'], ['(', '('], [')', ')'], [',', ','], ['∈', 'in']
        ];
        while (i < text.length) {
            const c = text[i];
            if (/\s/.test(c)) {
                i++;
                continue;
            }
            const sym = symbols.find(function (s) { return text.startsWith(s[0], i); });
            if (sym) {
                tokens.push({ t: sym[1], pos: i });
                i += sym[0].length;
                continue;
            }
            const word = /^[A-Za-zА-Яа-яЁё_][A-Za-zА-Яа-яЁё_0-9]*/.exec(text.slice(i));
            if (word) {
                const lower = word[0].toLowerCase();
                if (WORD_OPS[lower]) {
                    tokens.push({ t: WORD_OPS[lower], pos: i });
                } else {
                    tokens.push({ t: 'id', v: word[0], pos: i });
                }
                i += word[0].length;
                continue;
            }
            const num = /^\d+/.exec(text.slice(i));
            if (num) {
                tokens.push({ t: 'num', v: parseInt(num[0], 10), pos: i });
                i += num[0].length;
                continue;
            }
            throw new Error('Непонятный символ «' + c + '» (позиция ' + (i + 1) + ')');
        }
        return tokens;
    }

    function parse(text) {
        const tokens = tokenize(text);
        let p = 0;

        function peek() { return tokens[p] ? tokens[p].t : null; }
        function take(t) {
            if (peek() !== t) {
                throw new Error(t === ')' ? 'Не хватает закрывающей скобки' : 'Ожидалось «' + t + '»');
            }
            return tokens[p++];
        }
        function binary(next, types) {
            return function () {
                let left = next();
                while (types.indexOf(peek()) >= 0) {
                    const op = tokens[p++].t;
                    left = { op: op, l: left, r: next() };
                }
                return left;
            };
        }
        function atom() {
            const t = peek();
            if (t === 'not') {
                p++;
                return { op: 'not', a: atom() };
            }
            if (t === '(') {
                p++;
                const e = expr();
                take(')');
                return e;
            }
            if (t === 'num') {
                const v = tokens[p++].v;
                if (v !== 0 && v !== 1) {
                    throw new Error('В логике бывают только константы 0 и 1');
                }
                return { op: 'const', v: v === 1 };
            }
            if (t === 'id') {
                const name = tokens[p++].v;
                if (/^(дел|del|div)$/i.test(name) && peek() === '(') {
                    p++;
                    const a = term();
                    take(',');
                    const b = term();
                    take(')');
                    return { op: 'div', a: a, b: b };
                }
                if (peek() === 'in') {
                    p++;
                    return { op: 'in', x: name, seg: take('id').v };
                }
                return { op: 'var', name: name };
            }
            throw new Error(t === null ? 'Выражение оборвалось' : 'Лишний символ в позиции ' + (tokens[p].pos + 1));
        }
        function term() {
            if (peek() === 'num') {
                return { num: tokens[p++].v };
            }
            return { name: take('id').v };
        }
        const and = binary(atom, ['and']);
        const or = binary(and, ['or']);
        const imp = binary(or, ['imp']);
        const expr = binary(imp, ['eq', 'xor']);
        if (!tokens.length) {
            throw new Error('Введите выражение');
        }
        const tree = expr();
        if (p < tokens.length) {
            throw new Error('Лишний символ в позиции ' + (tokens[p].pos + 1));
        }
        return tree;
    }

    function evaluate(node, env) {
        switch (node.op) {
            case 'const': return node.v;
            case 'var': return env.bool(node.name);
            case 'in': return env.inSeg(node.seg);
            case 'div': {
                const a = env.num(node.a);
                const b = env.num(node.b);
                return b !== 0 && a % b === 0;
            }
            case 'not': return !evaluate(node.a, env);
            case 'and': return evaluate(node.l, env) && evaluate(node.r, env);
            case 'or': return evaluate(node.l, env) || evaluate(node.r, env);
            case 'imp': return !evaluate(node.l, env) || evaluate(node.r, env);
            case 'eq': return evaluate(node.l, env) === evaluate(node.r, env);
            case 'xor': return evaluate(node.l, env) !== evaluate(node.r, env);
        }
        throw new Error('?');
    }

    function prec(node) {
        return OPS[node.op] ? OPS[node.op].prec : 6;
    }

    function show(node) {
        switch (node.op) {
            case 'const': return node.v ? '1' : '0';
            case 'var': return node.name;
            case 'in': return node.x + '∈' + node.seg;
            case 'div': return 'ДЕЛ(' + termText(node.a) + ', ' + termText(node.b) + ')';
            case 'not': return '¬' + (prec(node.a) >= 5 ? show(node.a) : '(' + show(node.a) + ')');
        }
        // Для новичка скобки нагляднее приоритетов: другая операция внутри всегда в скобках.
        const assoc = node.op === 'and' || node.op === 'or';
        function wrap(child) {
            const binaryChild = !!child.l;
            const same = child.op === node.op && assoc;
            return binaryChild && !same ? '(' + show(child) + ')' : show(child);
        }
        return wrap(node.l) + ' ' + OPS[node.op].sym + ' ' + wrap(node.r);
    }

    function termText(t) {
        return t.num !== undefined ? String(t.num) : t.name;
    }

    // Подвыражения в порядке вычисления (сначала внутренние), без повторов.
    function steps(node, out) {
        out = out || [];
        if (node.op === 'not') {
            steps(node.a, out);
        } else if (node.l) {
            steps(node.l, out);
            steps(node.r, out);
        }
        if (['var', 'const', 'in', 'div'].indexOf(node.op) < 0) {
            const text = show(node);
            if (!out.some(function (s) { return s.text === text; })) {
                out.push({ node: node, text: text });
            }
        }
        return out;
    }

    function variables(node, out) {
        out = out || [];
        if (node.op === 'var' && out.indexOf(node.name) < 0) {
            out.push(node.name);
        }
        ['a', 'l', 'r'].forEach(function (k) {
            if (node[k] && node[k].op) {
                variables(node[k], out);
            }
        });
        return out;
    }

    function atoms(node, out) {
        out = out || [];
        if (node.op === 'in' || node.op === 'div') {
            const text = show(node);
            if (!out.some(function (s) { return s.text === text; })) {
                out.push({ node: node, text: text });
            }
        }
        ['a', 'l', 'r'].forEach(function (k) {
            if (node[k] && node[k].op) {
                atoms(node[k], out);
            }
        });
        return out;
    }

    function bit(v) {
        return span(v ? 'w-bit w-one' : 'w-bit w-zero', v ? '1' : '0');
    }

    // ================= Виджеты =================

    const widgets = {};

    // ---------- Сколько бит на N вариантов ----------
    widgets.bits = function (root) {
        frame(root, 'Сколько бит нужно на N вариантов');
        const n = numberInput(root.dataset.n || 30, 1, 1000000);
        const out = el('div');
        append(root, el('div', 'w-controls', null), out);
        root.querySelector('.w-controls').appendChild(field('Количество вариантов N', n));
        onInput([n], function () {
            out.replaceChildren();
            const N = Math.floor(+n.value);
            if (!(N >= 1) || N > 1000000) {
                out.appendChild(message('Введи целое N от 1 до 1 000 000', 'warn'));
                return;
            }
            const i = ceilLog2(N);
            const ladder = el('div', 'w-ladder');
            const top = Math.max(i + 2, 8);
            for (let k = 0; k <= Math.min(top, 20); k++) {
                const step = el('div', 'w-step');
                if (k === i) {
                    step.classList.add('w-step-hit');
                } else if (k < i) {
                    step.classList.add('w-step-low');
                }
                append(step, span('w-step-pow', '2' + sup(k)), span('w-step-val', fmt(Math.pow(2, k))));
                ladder.appendChild(step);
            }
            out.appendChild(ladder);
            const text = el('p', 'w-result');
            if (N === 1) {
                append(text, 'Один вариант — выбирать не из чего, нужно ', span('hl-green', '0 бит'), '.');
            } else if (Math.pow(2, i) === N) {
                append(text, 'N = ' + fmt(N) + ' = 2' + sup(i) + ' — ровно степень двойки, значит нужно ',
                    span('hl-green', i + ' бит'), '.');
            } else {
                append(text, '2' + sup(i - 1) + ' = ' + fmt(Math.pow(2, i - 1)) + ' < ' + fmt(N) + ' ≤ 2' + sup(i) + ' = '
                    + fmt(Math.pow(2, i)) + '. ' + (i - 1) + ' бит хватит только на ' + fmt(Math.pow(2, i - 1))
                    + ' вариантов, поэтому нужно ', span('hl-green', i + ' бит'),
                    ' (лишние ' + fmt(Math.pow(2, i) - N) + ' кодов останутся свободными).');
            }
            out.appendChild(text);
        });
    };

    // ---------- Перевод единиц измерения ----------
    widgets.units = function (root) {
        frame(root, 'Перевод единиц информации');
        const units = [['0', 'бит'], ['1', 'байт'], ['2', 'Кбайт'], ['3', 'Мбайт'], ['4', 'Гбайт']];
        const factor = [1, 8, 8 * 1024, 8 * 1024 * 1024, 8 * 1024 * 1024 * 1024];
        const value = numberInput(root.dataset.value || 3, 0);
        value.step = 'any';
        const unit = select(units, root.dataset.unit || '2');
        const controls = append(el('div', 'w-controls'), field('Значение', value), field('Единица', unit));
        const out = el('div');
        append(root, controls, out);
        onInput([value, unit], function () {
            out.replaceChildren();
            const v = +value.value;
            if (!(v >= 0)) {
                out.appendChild(message('Введи неотрицательное число', 'warn'));
                return;
            }
            const bits = v * factor[+unit.value];
            const table = el('table', 'w-table');
            units.forEach(function (u, idx) {
                const tr = el('tr');
                if (idx === +unit.value) {
                    tr.className = 'w-row-hit';
                }
                const val = bits / factor[idx];
                const exact = Number.isInteger(val) && val > 0 ? powerText(val) : '';
                append(tr, el('td', null, u[1]), el('td', 'w-num', fmt(val)),
                    el('td', 'hint', exact && exact !== String(val) ? '= ' + exact : ''));
                table.appendChild(tr);
            });
            const chain = el('p', 'hint',
                'Вверх по лестнице (к крупным единицам) — делим: бит ÷8 → байт ÷1024 → Кбайт ÷1024 → Мбайт ÷1024 → Гбайт. Вниз — умножаем.');
            append(out, table, chain);
        });
    };

    // ---------- Перевод между системами счисления ----------
    const DIGITS = '0123456789ABCDEF';

    function parseInBase(text, base) {
        const s = text.trim().toUpperCase();
        if (!s) {
            throw new Error('Введи число');
        }
        let v = 0n;
        for (const ch of s) {
            const d = DIGITS.indexOf(ch);
            if (d < 0 || d >= base) {
                throw new Error('Цифры «' + ch + '» нет в системе с основанием ' + base
                    + (base <= 10 ? ' (там цифры от 0 до ' + (base - 1) + ')' : ' (там цифры 0–9 и A–' + DIGITS[base - 1] + ')'));
            }
            v = v * BigInt(base) + BigInt(d);
        }
        return v;
    }

    function toBase(v, base) {
        if (v === 0n) {
            return '0';
        }
        let s = '';
        const b = BigInt(base);
        while (v > 0n) {
            s = DIGITS[Number(v % b)] + s;
            v /= b;
        }
        return s;
    }

    widgets['base-converter'] = function (root) {
        frame(root, 'Переводчик чисел: по шагам');
        const bases = [];
        for (let b = 2; b <= 16; b++) {
            bases.push([String(b), String(b)]);
        }
        const num = input(root.dataset.number || '345', { type: 'text', spellcheck: 'false', autocomplete: 'off' });
        num.className = 'mono';
        const from = select(bases, root.dataset.from || '10');
        const to = select(bases, root.dataset.to || '2');
        const out = el('div');
        append(root, append(el('div', 'w-controls'), field('Число', num), field('Из системы', from),
            field('В систему', to)), out);
        onInput([num, from, to], function () {
            out.replaceChildren();
            const fb = +from.value;
            const tb = +to.value;
            let v;
            try {
                v = parseInBase(num.value, fb);
            } catch (e) {
                out.appendChild(message(e.message, 'warn'));
                return;
            }
            if (v > 10n ** 30n) {
                out.appendChild(message('Слишком большое число для пошагового разбора', 'warn'));
                return;
            }
            const digits = num.value.trim().toUpperCase().replace(/^0+(?=.)/, '');
            if (fb !== 10) {
                out.appendChild(el('h4', 'w-sub', 'Шаг 1. Из системы ' + fb + ' в десятичную: цифра × вес разряда'));
                const table = el('table', 'w-table w-weights');
                const rowPow = el('tr');
                const rowDigit = el('tr');
                const rowProd = el('tr');
                append(rowPow, el('th', null, 'вес'));
                append(rowDigit, el('th', null, 'цифра'));
                append(rowProd, el('th', null, 'цифра × вес'));
                const parts = [];
                for (let i = 0; i < digits.length; i++) {
                    const power = digits.length - 1 - i;
                    const d = DIGITS.indexOf(digits[i]);
                    const weight = BigInt(fb) ** BigInt(power);
                    append(rowPow, el('td', 'hint', fb + sup(power) + ' = ' + fmt(weight)));
                    append(rowDigit, el('td', 'w-digit', digits[i] + (d >= 10 ? ' (' + d + ')' : '')));
                    append(rowProd, el('td', d ? 'w-num' : 'w-num hint', fmt(BigInt(d) * weight)));
                    if (d) {
                        parts.push(fmt(BigInt(d) * weight));
                    }
                }
                append(table, rowPow, rowDigit, rowProd);
                append(out, el('div', 'w-scroll'), null);
                out.lastChild.appendChild(table);
                const sum = el('p', 'w-result');
                append(sum, digits + sub(fb) + ' = ' + (parts.join(' + ') || '0') + ' = ', span('hl-green', fmt(v) + sub(10)));
                out.appendChild(sum);
            }
            if (tb !== 10) {
                out.appendChild(el('h4', 'w-sub', (fb !== 10 ? 'Шаг 2. ' : '') + 'Из десятичной в систему ' + tb
                    + ': делим на ' + tb + ', записываем остатки'));
                const rows = [];
                let cur = v;
                const b = BigInt(tb);
                if (cur === 0n) {
                    rows.push([0n, 0n, 0n]);
                }
                while (cur > 0n) {
                    rows.push([cur, cur / b, cur % b]);
                    cur /= b;
                }
                const table = el('table', 'w-table w-division');
                append(table, append(el('tr'), el('th', null, 'делимое'), el('th', null, ': ' + tb + ' ='),
                    el('th', null, 'частное'), el('th', null, 'остаток')));
                rows.forEach(function (r, idx) {
                    const rem = Number(r[2]);
                    append(table, append(el('tr', 'w-div-row'), el('td', 'w-num', fmt(r[0])), el('td', 'hint', ': ' + tb),
                        el('td', 'w-num', fmt(r[1])),
                        el('td', 'w-num', null)));
                    table.lastChild.lastChild.appendChild(span('w-rem w-rem-' + (idx % 6),
                        DIGITS[rem] + (rem >= 10 ? ' (' + rem + ')' : '')));
                });
                const scroll = el('div', 'w-scroll');
                scroll.appendChild(table);
                out.appendChild(scroll);
                const result = el('p', 'w-result');
                append(result, 'Остатки читаем ', el('b', null, 'снизу вверх'), ': ');
                rows.slice().reverse().forEach(function (r, i) {
                    const idx = rows.length - 1 - i;
                    result.appendChild(span('w-rem w-rem-' + (idx % 6), DIGITS[Number(r[2])]));
                });
                append(result, ' → ', span('hl-green', toBase(v, tb) + sub(tb)));
                out.appendChild(result);
                stepper(out, Array.prototype.slice.call(table.querySelectorAll('.w-div-row')), result);
            }
            if (fb === tb) {
                out.appendChild(message('Системы совпадают — переводить нечего.'));
            } else if (tb === 10 && fb !== 10) {
                // уже показано
            }
            const check = el('p', 'hint', 'Проверка в Python: ' + (tb === 10 ? 'int(\'' + digits + '\', ' + fb + ')'
                : fb === 10 && [2, 8, 16].indexOf(tb) >= 0 ? { 2: 'bin', 8: 'oct', 16: 'hex' }[tb] + '(' + v + ')'
                    : 'int(\'' + digits + '\', ' + fb + ') и функция перевода из урока 11 курса Python'));
            out.appendChild(check);
        });
    };

    // Кнопки «по шагам» для строк таблицы деления.
    function stepper(container, rows, result) {
        if (rows.length < 2) {
            return;
        }
        const bar = el('div', 'steps-bar');
        const start = button('Показать по шагам', 'w-button w-light');
        const next = button('Следующий шаг →');
        next.hidden = true;
        let shown = rows.length;
        function update() {
            rows.forEach(function (r, i) { r.hidden = i >= shown; });
            result.hidden = shown < rows.length;
            next.hidden = shown >= rows.length;
            start.hidden = shown < rows.length;
        }
        start.addEventListener('click', function () { shown = 1; update(); });
        next.addEventListener('click', function () { shown++; update(); });
        append(bar, start, next);
        container.insertBefore(bar, result);
        update();
    }

    // ---------- Триады и тетрады ----------
    widgets['bin-groups'] = function (root) {
        frame(root, 'Быстрый перевод: группы по 3 и по 4 бита');
        const num = input(root.dataset.number || '1011010110', { type: 'text', spellcheck: 'false', autocomplete: 'off' });
        num.className = 'mono';
        const base = select([['2', 'двоичное'], ['8', 'восьмеричное'], ['16', 'шестнадцатеричное']], root.dataset.base || '2');
        const out = el('div');
        append(root, append(el('div', 'w-controls'), field('Число', num), field('Система', base)), out);
        onInput([num, base], function () {
            out.replaceChildren();
            let v;
            try {
                v = parseInBase(num.value, +base.value);
            } catch (e) {
                out.appendChild(message(e.message, 'warn'));
                return;
            }
            if (v > 2n ** 64n) {
                out.appendChild(message('Возьми число поменьше', 'warn'));
                return;
            }
            const bin = toBase(v, 2);
            [[3, 8, 'Триады (по 3 бита) → восьмеричная система'], [4, 16, 'Тетрады (по 4 бита) → шестнадцатеричная система']]
                .forEach(function (g) {
                    const size = g[0];
                    const padded = bin.padStart(Math.ceil(bin.length / size) * size, '0');
                    out.appendChild(el('h4', 'w-sub', g[2]));
                    const row = el('div', 'w-groups');
                    for (let i = 0; i < padded.length; i += size) {
                        const chunk = padded.slice(i, i + size);
                        const group = el('div', 'w-group w-group-' + ((i / size) % 4));
                        const bits = el('div', 'w-group-bits mono');
                        chunk.split('').forEach(function (c, j) {
                            const isPad = i + j < padded.length - bin.length;
                            bits.appendChild(span(isPad ? 'w-pad' : '', c));
                        });
                        const weights = el('div', 'w-group-weights hint',
                            size === 3 ? '4 2 1' : '8 4 2 1');
                        append(group, weights, bits, el('div', 'w-group-digit', DIGITS[parseInt(chunk, 2)]));
                        row.appendChild(group);
                    }
                    out.appendChild(row);
                    const res = el('p', 'w-result');
                    append(res, bin + sub(2) + ' = ', span('hl-green', toBase(v, g[1]) + sub(g[1])));
                    out.appendChild(res);
                });
            if (bin.length % 3 || bin.length % 4) {
                out.appendChild(el('p', 'hint', 'Серые нули слева дописаны, чтобы получились полные группы. '
                    + 'Группы отсчитываются справа налево!'));
            }
        });
    };

    // ---------- Калькулятор логического выражения ----------
    widgets['logic-calc'] = function (root) {
        frame(root, 'Калькулятор логического выражения');
        const expr = input(root.dataset.expr || '(x ∨ y) → ¬z', { type: 'text', spellcheck: 'false', autocomplete: 'off' });
        expr.className = 'mono w-expr';
        const keys = el('div', 'w-keys');
        ['¬', '∧', '∨', '→', '≡', '⊕', '(', ')'].forEach(function (s) {
            const k = button(s, 'w-key');
            k.addEventListener('click', function () {
                const start = expr.selectionStart === null ? expr.value.length : expr.selectionStart;
                const end = expr.selectionEnd === null ? start : expr.selectionEnd;
                const pad = s === '(' || s === ')' || s === '¬' ? s : ' ' + s + ' ';
                expr.value = expr.value.slice(0, start) + pad + expr.value.slice(end);
                expr.focus();
                expr.selectionStart = expr.selectionEnd = start + pad.length;
                expr.dispatchEvent(new Event('input'));
            });
            keys.appendChild(k);
        });
        const help = el('p', 'hint', 'Можно писать и с клавиатуры: not, and, or, ->, ==, а также !, &, |.');
        const vars = el('div', 'w-vars');
        const out = el('div');
        const tableBtn = button('Показать всю таблицу истинности', 'w-button w-light');
        const tableBox = el('div', 'w-scroll');
        tableBox.hidden = true;
        append(root, field('Выражение', expr), keys, help, vars, out, tableBtn, tableBox);
        const values = {};
        let tree = null;

        function render() {
            out.replaceChildren();
            vars.replaceChildren();
            try {
                tree = parse(expr.value);
            } catch (e) {
                tree = null;
                out.appendChild(message(e.message, 'warn'));
                tableBtn.hidden = true;
                tableBox.hidden = true;
                return;
            }
            tableBtn.hidden = false;
            const names = variables(tree).sort();
            if (names.length > 6) {
                out.appendChild(message('Не больше 6 переменных', 'warn'));
                return;
            }
            names.forEach(function (name) {
                if (values[name] === undefined) {
                    values[name] = false;
                }
                const b = button(name + ' = ' + (values[name] ? 1 : 0), 'w-toggle ' + (values[name] ? 'w-on' : 'w-off'));
                b.addEventListener('click', function () {
                    values[name] = !values[name];
                    render();
                });
                vars.appendChild(b);
            });
            const env = { bool: function (n) { return values[n]; } };
            const list = el('ol', 'w-eval');
            steps(tree).forEach(function (s) {
                const v = evaluate(s.node, env);
                append(list, append(el('li'), el('code', null, s.text), ' = ', bit(v)));
            });
            out.appendChild(el('div', 'hint', names.length ? 'Нажимай на переменные, чтобы менять их значения. '
                + 'Скобки считаются по порядку:' : 'Порядок вычисления:'));
            out.appendChild(list);
            const result = el('p', 'w-result');
            append(result, 'F = ', bit(evaluate(tree, env)));
            out.appendChild(result);
            if (!tableBox.hidden) {
                renderTable();
            }
        }

        function renderTable() {
            tableBox.replaceChildren();
            if (!tree) {
                return;
            }
            const names = variables(tree).sort();
            const cols = steps(tree);
            const table = el('table', 'w-table w-truth');
            const head = el('tr');
            names.forEach(function (n) { head.appendChild(el('th', null, n)); });
            cols.forEach(function (c, i) {
                head.appendChild(el('th', i === cols.length - 1 ? 'w-col-f' : 'w-col-mid', c.text));
            });
            table.appendChild(head);
            let ones = 0;
            for (let mask = 0; mask < (1 << names.length); mask++) {
                const vals = {};
                names.forEach(function (n, i) { vals[n] = ((mask >> (names.length - 1 - i)) & 1) === 1; });
                const env = { bool: function (n) { return vals[n]; } };
                const tr = el('tr');
                names.forEach(function (n) { append(tr, append(el('td'), bit(vals[n]))); });
                cols.forEach(function (c, i) {
                    const v = evaluate(c.node, env);
                    append(tr, append(el('td', i === cols.length - 1 ? 'w-col-f' : 'w-col-mid'), bit(v)));
                    if (i === cols.length - 1 && v) {
                        ones++;
                    }
                });
                table.appendChild(tr);
            }
            append(tableBox, table, el('p', 'w-result', 'Строк: ' + (1 << names.length) + '. F = 1 в ' + ones
                + ' строках, F = 0 в ' + ((1 << names.length) - ones) + '.'));
        }

        tableBtn.addEventListener('click', function () {
            tableBox.hidden = !tableBox.hidden;
            tableBtn.textContent = tableBox.hidden ? 'Показать всю таблицу истинности' : 'Скрыть таблицу';
            renderTable();
        });
        expr.addEventListener('input', render);
        render();
    };

    // ---------- Числовая прямая: отрезки P, Q, A ----------
    function parseSegment(text) {
        const m = /^\s*(-?\d+(?:\.\d+)?)\s*[,;]\s*(-?\d+(?:\.\d+)?)\s*$/.exec(text || '');
        return m ? [+m[1], +m[2]] : null;
    }

    widgets['number-line'] = function (root) {
        frame(root, 'Числовая прямая: подбери отрезок A');
        const segs = {};
        ['p', 'q', 'r'].forEach(function (k) {
            const s = parseSegment(root.dataset[k]);
            if (s) {
                segs[k.toUpperCase()] = s;
            }
        });
        const min = +(root.dataset.min || 0);
        const max = +(root.dataset.max || 100);
        const expr = input(root.dataset.expr || '(x∈P → x∈Q) ∨ x∈A', { type: 'text', spellcheck: 'false' });
        expr.className = 'mono w-expr';
        const a0 = parseSegment(root.dataset.a) || [min + 5, min + 10];
        const aStart = numberInput(a0[0], min, max);
        const aEnd = numberInput(a0[1], min, max);
        aStart.step = aEnd.step = '1';
        const aStartRange = input(a0[0], { type: 'range', min: min, max: max, step: 1 });
        const aEndRange = input(a0[1], { type: 'range', min: min, max: max, step: 1 });
        const legend = el('p', 'hint');
        Object.keys(segs).forEach(function (k) {
            legend.appendChild(span('w-seg-label w-seg-' + k, k + ' = [' + segs[k][0] + '; ' + segs[k][1] + ']'));
        });
        const pic = el('div', 'w-scroll');
        const out = el('div');
        append(root, field('Формула (должна быть истинна при любом x)', expr), legend,
            append(el('div', 'w-controls'),
                field('Начало A', append(el('span', 'w-range'), aStartRange, aStart)),
                field('Конец A', append(el('span', 'w-range'), aEndRange, aEnd))), pic, out);

        function sync(from, to) {
            from.addEventListener('input', function () { to.value = from.value; render(); });
        }
        sync(aStartRange, aStart);
        sync(aStart, aStartRange);
        sync(aEndRange, aEnd);
        sync(aEnd, aEndRange);
        expr.addEventListener('input', render);

        function render() {
            out.replaceChildren();
            pic.replaceChildren();
            let tree;
            try {
                tree = parse(expr.value);
            } catch (e) {
                out.appendChild(message(e.message, 'warn'));
                return;
            }
            let as = +aStart.value;
            let ae = +aEnd.value;
            if (as > ae) {
                const t = as;
                as = ae;
                ae = t;
            }
            const all = Object.assign({}, segs, { A: [as, ae] });
            // Значение формулы на кусках прямой между «особыми» точками — концами всех отрезков.
            const points = [];
            Object.keys(all).forEach(function (k) { points.push(all[k][0], all[k][1]); });
            const crit = points.filter(function (v, i) { return points.indexOf(v) === i; }).sort(function (a, b) { return a - b; });
            function value(x) {
                return evaluate(tree, {
                    inSeg: function (name) {
                        const s = all[name];
                        if (!s) {
                            throw new Error('Неизвестный отрезок ' + name);
                        }
                        return x >= s[0] && x <= s[1];
                    },
                    bool: function (name) {
                        throw new Error('Пиши x∈' + name + ' вместо ' + name);
                    }
                });
            }
            const pieces = [];   // {lo, hi, loIn, hiIn, ok}
            try {
                pieces.push({ lo: -Infinity, hi: crit[0], ok: value(crit[0] - 1) });
                crit.forEach(function (c, i) {
                    pieces.push({ lo: c, hi: c, point: true, ok: value(c) });
                    const next = i + 1 < crit.length ? crit[i + 1] : Infinity;
                    pieces.push({ lo: c, hi: next, ok: value(next === Infinity ? c + 1 : (c + next) / 2) });
                });
            } catch (e) {
                out.appendChild(message(e.message, 'warn'));
                return;
            }
            // Куски идут по прямой подряд, поэтому соседние «ложные» куски склеиваются в один промежуток.
            const bad = [];
            let open = null;
            pieces.forEach(function (p) {
                if (p.ok) {
                    open = null;
                    return;
                }
                if (open) {
                    open.hi = p.hi;
                    open.hiIn = !!p.point;
                } else {
                    open = { lo: p.lo, hi: p.hi, loIn: !!p.point, hiIn: !!p.point };
                    bad.push(open);
                }
            });
            drawLine(pic, all, bad, min, max);
            if (!bad.length) {
                const ok = el('p', 'w-result');
                append(ok, '✅ Формула истинна при любом x. Длина отрезка A = ' + fmt(ae - as) + '.');
                out.appendChild(ok);
            } else {
                const text = el('p', 'w-result');
                append(text, '❌ Формула ложна при x ∈ ', span('hl-red', bad.map(intervalText).join(' ∪ ')),
                    '. Красные зоны нужно закрыть отрезком A.');
                out.appendChild(text);
            }
        }

        function intervalText(b) {
            if (b.lo === b.hi) {
                return '{' + fmt(b.lo) + '}';
            }
            return (b.loIn ? '[' : '(') + (b.lo === -Infinity ? '−∞' : fmt(b.lo)) + '; '
                + (b.hi === Infinity ? '+∞' : fmt(b.hi)) + (b.hiIn ? ']' : ')');
        }

        render();
    };

    function drawLine(container, segs, bad, min, max) {
        const W = 640;
        const names = Object.keys(segs);
        const H = 46 + names.length * 22;
        const pad = 24;
        function X(v) {
            return pad + (Math.max(min, Math.min(max, v)) - min) / (max - min) * (W - 2 * pad);
        }
        const s = svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'w-svg', role: 'img', 'aria-label': 'Числовая прямая' });
        const axisY = H - 26;
        bad.forEach(function (b) {
            const x1 = X(b.lo);
            const x2 = X(b.hi);
            s.appendChild(svg('rect', { x: x1 - (b.lo === b.hi ? 2 : 0), y: 4, width: Math.max(4, x2 - x1), height: axisY - 4,
                class: 'w-bad' }));
        });
        s.appendChild(svg('line', { x1: pad - 10, y1: axisY, x2: W - pad + 10, y2: axisY, class: 'w-axis' }));
        const step = (max - min) / 10;
        for (let v = min; v <= max + 1e-9; v += step) {
            s.appendChild(svg('line', { x1: X(v), y1: axisY - 4, x2: X(v), y2: axisY + 4, class: 'w-axis' }));
            s.appendChild(svg('text', { x: X(v), y: axisY + 18, 'text-anchor': 'middle', class: 'w-tick' }, fmt(Math.round(v * 100) / 100)));
        }
        names.forEach(function (k, i) {
            const y = 12 + i * 22;
            const seg = segs[k];
            s.appendChild(svg('line', { x1: X(seg[0]), y1: y, x2: X(seg[1]), y2: y, class: 'w-seg w-seg-' + k }));
            s.appendChild(svg('circle', { cx: X(seg[0]), cy: y, r: 4, class: 'w-seg-dot w-seg-' + k }));
            s.appendChild(svg('circle', { cx: X(seg[1]), cy: y, r: 4, class: 'w-seg-dot w-seg-' + k }));
            s.appendChild(svg('text', { x: Math.min(X(seg[1]) + 8, W - 14), y: y + 4, class: 'w-seg-name' }, k));
        });
        container.appendChild(s);
    }

    // ---------- ДЕЛ(x, A): сетка чисел ----------
    widgets['div-grid'] = function (root) {
        frame(root, 'Делимость: подбери A');
        const expr = input(root.dataset.expr || 'ДЕЛ(x, A) ∨ (ДЕЛ(x, 6) → ¬ДЕЛ(x, 4))', { type: 'text', spellcheck: 'false' });
        expr.className = 'mono w-expr';
        const limit = Math.min(+(root.dataset.max || 120), 400);
        const a = numberInput(root.dataset.a || 1, 1, 1000);
        const goalMax = root.dataset.goal === 'max';
        const find = button(goalMax ? 'Найти наибольшее подходящее A' : 'Найти наименьшее подходящее A', 'w-button w-light');
        const grid = el('div', 'w-grid');
        const out = el('div');
        append(root, field('Формула (истинна при любом натуральном x)', expr),
            append(el('div', 'w-controls'), field('A', a), find), out, grid,
            el('p', 'hint', 'Показаны x от 1 до ' + limit + '. Красные клетки — где формула ложна.'));

        function check(tree, A) {
            const bad = [];
            for (let x = 1; x <= limit; x++) {
                const v = evaluate(tree, {
                    num: function (t) {
                        if (t.num !== undefined) {
                            return t.num;
                        }
                        if (t.name === 'x') {
                            return x;
                        }
                        if (t.name === 'A') {
                            return A;
                        }
                        throw new Error('Неизвестное имя ' + t.name);
                    },
                    bool: function (n) { throw new Error('Неизвестная переменная ' + n); }
                });
                if (!v) {
                    bad.push(x);
                }
            }
            return bad;
        }

        function render() {
            out.replaceChildren();
            grid.replaceChildren();
            let tree;
            let bad;
            try {
                tree = parse(expr.value);
                bad = check(tree, Math.max(1, Math.floor(+a.value)));
            } catch (e) {
                out.appendChild(message(e.message, 'warn'));
                return;
            }
            for (let x = 1; x <= limit; x++) {
                grid.appendChild(el('span', bad.indexOf(x) >= 0 ? 'w-cell w-cell-bad' : 'w-cell', String(x)));
            }
            out.appendChild(bad.length
                ? message('❌ При A = ' + a.value + ' формула ложна, например, при x = ' + bad.slice(0, 8).join(', ')
                    + (bad.length > 8 ? '…' : '') + '.', 'wrong')
                : message('✅ При A = ' + a.value + ' формула истинна для всех x от 1 до ' + limit + '.', 'right'));
        }

        find.addEventListener('click', function () {
            let tree;
            try {
                tree = parse(expr.value);
            } catch (e) {
                return;
            }
            for (let k = 1; k <= 1000; k++) {
                const A = goalMax ? 1001 - k : k;
                if (!check(tree, A).length) {
                    a.value = A;
                    render();
                    return;
                }
            }
            out.replaceChildren(message('A от 1 до 1000 не нашлось', 'warn'));
        });
        onInput([expr, a], render);
    };

    // ---------- Поразрядные операции ----------
    widgets.bitwise = function (root) {
        frame(root, 'Поразрядная конъюнкция столбиком');
        const a = numberInput(root.dataset.a || 29, 0, 4294967295);
        const b = numberInput(root.dataset.b || 12, 0, 4294967295);
        const op = select([['and', '& (И)'], ['or', '| (ИЛИ)'], ['xor', '^ (исключающее ИЛИ)']], root.dataset.op || 'and');
        const out = el('div');
        append(root, append(el('div', 'w-controls'), field('Первое число', a), field('Второе число', b), field('Операция', op)), out);
        onInput([a, b, op], function () {
            out.replaceChildren();
            const x = Math.floor(+a.value);
            const y = Math.floor(+b.value);
            if (!(x >= 0 && y >= 0) || x > 4294967295 || y > 4294967295) {
                out.appendChild(message('Числа от 0 до 4 294 967 295', 'warn'));
                return;
            }
            const r = op.value === 'and' ? (x & y) >>> 0 : op.value === 'or' ? (x | y) >>> 0 : (x ^ y) >>> 0;
            const len = Math.max(x.toString(2).length, y.toString(2).length, 1);
            const rows = [[x, String(x)], [y, String(y)], [r, '= ' + r]];
            const table = el('table', 'w-table w-bits');
            rows.forEach(function (row, idx) {
                const tr = el('tr', idx === 2 ? 'w-sum-row' : '');
                const bits = row[0].toString(2).padStart(len, '0');
                bits.split('').forEach(function (c, i) {
                    const td = el('td', 'mono', c);
                    if (idx === 2 && c === '1') {
                        td.classList.add('w-hit');
                    }
                    if (idx < 2 && op.value === 'and' && rows[2][0].toString(2).padStart(len, '0')[i] === '1') {
                        td.classList.add('w-hit-soft');
                    }
                    tr.appendChild(td);
                });
                tr.appendChild(el('td', 'hint', row[1]));
                table.appendChild(tr);
            });
            const scroll = el('div', 'w-scroll');
            scroll.appendChild(table);
            const rule = { and: 'В результате 1 только там, где в обоих числах 1.',
                or: 'В результате 1 там, где хотя бы в одном числе 1.',
                xor: 'В результате 1 там, где биты разные.' }[op.value];
            append(out, scroll, el('p', 'hint', rule));
        });
    };

    // ---------- Конструктор дерева Фано ----------
    widgets['fano-tree'] = function (root) {
        frame(root, 'Конструктор дерева Фано');
        const area = el('textarea', 'mono w-codes');
        area.rows = 5;
        area.spellcheck = false;
        area.value = (root.dataset.codes || 'А=0;Б=10;В=110').split(';').join('\n');
        const pic = el('div', 'w-scroll');
        const out = el('div');
        append(root, field('Коды: по одному в строке, «буква=код»', area), pic, out);

        function render() {
            pic.replaceChildren();
            out.replaceChildren();
            const codes = [];
            const errors = [];
            area.value.split('\n').forEach(function (line, i) {
                const t = line.trim();
                if (!t) {
                    return;
                }
                const m = /^(\S+)\s*[=:\s]\s*([01]+)$/.exec(t);
                if (!m) {
                    errors.push('Строка ' + (i + 1) + ': пиши так: А=010 (код только из 0 и 1)');
                    return;
                }
                if (m[2].length > 7) {
                    errors.push('Строка ' + (i + 1) + ': код длиннее 7 символов не поместится на картинке');
                    return;
                }
                codes.push({ letter: m[1], code: m[2] });
            });
            if (errors.length) {
                errors.forEach(function (e) { out.appendChild(message(e, 'warn')); });
                return;
            }
            const problems = [];
            const badCodes = {};
            codes.forEach(function (a, i) {
                codes.forEach(function (b, j) {
                    if (i === j) {
                        return;
                    }
                    if (a.code === b.code && i < j) {
                        problems.push('У ' + a.letter + ' и ' + b.letter + ' одинаковый код ' + a.code + '.');
                        badCodes[a.code] = true;
                    } else if (a.code !== b.code && b.code.startsWith(a.code)) {
                        problems.push('Код ' + a.letter + ' = ' + a.code + ' — начало кода ' + b.letter + ' = ' + b.code + '.');
                        badCodes[a.code] = true;
                        badCodes[b.code] = true;
                    }
                });
            });
            const depth = Math.max(3, codes.reduce(function (m, c) { return Math.max(m, c.code.length); }, 0) + 1);
            // Свободные узлы: на них и выше нет кодов, и через них не проходит ни один код.
            function used(prefix) {
                return codes.some(function (c) { return c.code.startsWith(prefix) || prefix.startsWith(c.code); });
            }
            let shortest = null;
            for (let d = 1; d <= depth && !shortest; d++) {
                for (let m = 0; m < (1 << d); m++) {
                    const s = m.toString(2).padStart(d, '0');
                    if (!used(s)) {
                        shortest = s;
                        break;
                    }
                }
            }
            drawFano(pic, codes, badCodes, depth, used);
            if (problems.length) {
                const box = el('div', 'w-msg wrong');
                box.appendChild(el('b', null, '❌ Условие Фано нарушено:'));
                const ul = el('ul');
                problems.forEach(function (p) { ul.appendChild(el('li', null, p)); });
                box.appendChild(ul);
                out.appendChild(box);
            } else {
                out.appendChild(message('✅ Условие Фано выполнено: ни один код не является началом другого. '
                    + 'Все буквы стоят в листьях дерева.', 'right'));
            }
            if (shortest) {
                const p = el('p', 'w-result');
                append(p, 'Самый короткий код, который можно добавить, не нарушая условие: ',
                    span('hl-green mono', shortest), ' (длина ' + shortest.length + '). Свободные ветки отмечены пунктиром.');
                out.appendChild(p);
            } else if (!problems.length) {
                out.appendChild(el('p', 'hint', 'Свободных веток до глубины ' + depth + ' нет: дерево заполнено.'));
            }
            const total = codes.reduce(function (s, c) { return s + c.code.length; }, 0);
            if (codes.length) {
                out.appendChild(el('p', 'hint', 'Сумма длин всех кодов: ' + total + '.'));
            }
        }

        area.addEventListener('input', render);
        render();
    };

    function drawFano(container, codes, badCodes, depth, used) {
        const W = Math.max(480, Math.pow(2, depth) * 22);
        const levelH = 56;
        const H = depth * levelH + 50;
        const s = svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'w-svg w-fano', role: 'img', 'aria-label': 'Дерево кодов' });
        function pos(prefix) {
            const d = prefix.length;
            const idx = d ? parseInt(prefix, 2) : 0;
            return { x: (idx + 0.5) / Math.pow(2, d) * W, y: 24 + d * levelH };
        }
        const letterAt = {};
        codes.forEach(function (c) { letterAt[c.code] = (letterAt[c.code] ? letterAt[c.code] + ',' : '') + c.letter; });
        // Рисуем узлы, через которые идут коды, и их «детей» — чтобы было видно свободные ветки.
        const nodes = { '': true };
        codes.forEach(function (c) {
            for (let i = 1; i <= c.code.length; i++) {
                nodes[c.code.slice(0, i)] = true;
            }
        });
        const draw = {};
        Object.keys(nodes).forEach(function (p) {
            draw[p] = true;
            if (p.length < depth && !letterAt[p]) {
                draw[p + '0'] = true;
                draw[p + '1'] = true;
            }
        });
        Object.keys(draw).forEach(function (p) {
            if (!p) {
                return;
            }
            const a = pos(p.slice(0, -1));
            const b = pos(p);
            const free = !used(p);
            s.appendChild(svg('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, class: free ? 'w-edge w-free' : 'w-edge' }));
            s.appendChild(svg('text', { x: (a.x + b.x) / 2 + (p.endsWith('0') ? -10 : 6), y: (a.y + b.y) / 2,
                class: 'w-edge-label' }, p.slice(-1)));
        });
        Object.keys(draw).forEach(function (p) {
            const q = pos(p);
            const letter = letterAt[p];
            const free = p && !used(p);
            const cls = letter ? (badCodes[p] ? 'w-node w-node-bad' : 'w-node w-node-leaf') : free ? 'w-node w-node-free' : 'w-node';
            s.appendChild(svg('circle', { cx: q.x, cy: q.y, r: letter ? 13 : 7, class: cls }));
            if (letter) {
                s.appendChild(svg('text', { x: q.x, y: q.y + 5, 'text-anchor': 'middle', class: 'w-node-text' }, letter));
                s.appendChild(svg('text', { x: q.x, y: q.y + 30, 'text-anchor': 'middle', class: 'w-node-code' }, p));
            }
        });
        container.appendChild(s);
    }

    // ---------- Пароли и сообщения ----------
    widgets.password = function (root) {
        frame(root, 'Калькулятор объёма паролей');
        const n = numberInput(root.dataset.alphabet || 26, 1, 100000);
        const len = numberInput(root.dataset.length || 15, 1, 100000);
        const users = numberInput(root.dataset.users || 30, 1, 10000000);
        const extra = numberInput(root.dataset.extra || 0, 0, 1000000);
        const out = el('div');
        append(root, append(el('div', 'w-controls'), field('Символов в алфавите', n), field('Длина пароля', len),
            field('Пользователей', users), field('Доп. байт на пользователя', extra)), out);
        onInput([n, len, users, extra], function () {
            out.replaceChildren();
            const N = Math.floor(+n.value);
            const L = Math.floor(+len.value);
            const K = Math.floor(+users.value);
            const E = Math.floor(+extra.value);
            if (!(N >= 1 && L >= 1 && K >= 1 && E >= 0)) {
                out.appendChild(message('Все величины — натуральные числа', 'warn'));
                return;
            }
            const i = ceilLog2(N);
            const bits = L * i;
            const bytes = Math.ceil(bits / 8);
            const per = bytes + E;
            const table = el('table', 'w-table');
            function row(what, how, result, warn) {
                const tr = el('tr', warn ? 'w-row-warn' : '');
                append(tr, el('td', null, what), el('td', 'hint', how), append(el('td', 'w-num'), result));
                table.appendChild(tr);
            }
            append(table, append(el('tr'), el('th', null, 'Что считаем'), el('th', null, 'Как'), el('th', null, 'Результат')));
            row('Бит на символ', N === 1 ? 'один символ — 0 бит' : '2' + sup(i - 1) + ' < ' + N + ' ≤ 2' + sup(i) + ', округляем вверх',
                span('hl-blue', i + ' бит'), Math.pow(2, i) !== N);
            row('Бит на пароль', L + ' · ' + i, bits + ' бит');
            row('Байт на пароль', bits + ' / 8 = ' + fmt(bits / 8) + (bits % 8 ? ' → округляем вверх' : ''),
                span('hl-blue', bytes + ' байт'), bits % 8 !== 0);
            if (E) {
                row('Байт на пользователя', bytes + ' + ' + E, per + ' байт');
            }
            row('Всего на ' + K + ' пользователей', K + ' · ' + per, span('hl-green', fmt(K * per) + ' байт'));
            const scroll = el('div', 'w-scroll');
            scroll.appendChild(table);
            out.appendChild(scroll);
            out.appendChild(el('p', 'hint', 'Жёлтые строки — места, где нужно округлять вверх. Это два разных округления!'));
        });
    };

    // ---------- Изображения и звук ----------
    widgets['media-calc'] = function (root) {
        frame(root, 'Объём изображения и звука');
        const tabs = el('div', 'w-tabs');
        const imgTab = button('Изображение', 'w-tab');
        const sndTab = button('Звук', 'w-tab');
        const netTab = button('Передача', 'w-tab');
        append(tabs, imgTab, sndTab, netTab);
        const body = el('div');
        append(root, tabs, body);

        function units(bits) {
            const lines = [fmt(bits) + ' бит'];
            if (bits % 8 === 0) {
                lines.push(fmt(bits / 8) + ' байт');
            } else {
                lines.push(fmt(bits / 8) + ' байт (дробное — значит, в задаче обычно округляют вверх)');
            }
            lines.push(fmt(bits / 8 / 1024) + ' Кбайт', fmt(bits / 8 / 1024 / 1024) + ' Мбайт');
            return lines.join(' = ');
        }

        function powers(factors, bits) {
            let k = 0;
            const parts = factors.map(function (f) {
                const p = powerSplit(f);
                k += p.k;
                return powerText(f);
            });
            const odd = factors.reduce(function (m, f) { return m * powerSplit(f).m; }, 1);
            return parts.join(' · ') + ' = ' + (odd === 1 ? '' : odd + ' · ') + '2' + sup(k) + ' бит = '
                + (odd === 1 ? '' : odd + ' · ') + '2' + sup(k - 13) + ' Кбайт';
        }

        function image() {
            const w = numberInput(root.dataset.w || 1024, 1);
            const h = numberInput(root.dataset.h || 768, 1);
            const colors = numberInput(root.dataset.colors || 256, 2);
            const out = el('div');
            body.replaceChildren(append(el('div', 'w-controls'), field('Ширина, пикселей', w), field('Высота, пикселей', h),
                field('Цветов в палитре', colors)), out);
            onInput([w, h, colors], function () {
                out.replaceChildren();
                const W = Math.floor(+w.value);
                const H = Math.floor(+h.value);
                const N = Math.floor(+colors.value);
                if (!(W >= 1 && H >= 1 && N >= 2)) {
                    out.appendChild(message('Размеры — натуральные числа, цветов не меньше 2', 'warn'));
                    return;
                }
                const i = ceilLog2(N);
                const bits = W * H * i;
                const list = el('ol', 'w-eval');
                append(list, el('li', null, 'Глубина цвета: ' + (Math.pow(2, i) === N ? N + ' = 2' + sup(i)
                    : '2' + sup(i - 1) + ' < ' + N + ' ≤ 2' + sup(i)) + ' → i = ' + i + ' бит на пиксель'),
                el('li', null, 'Пикселей: ' + W + ' · ' + H + ' = ' + fmt(W * H)),
                el('li', null, 'Объём: ' + W + ' · ' + H + ' · ' + i + ' = ' + units(bits)),
                el('li', null, 'Через степени двойки: ' + powers([W, H, i], bits)));
                out.appendChild(list);
            });
        }

        function sound() {
            const f = numberInput(root.dataset.freq || 48, 1);
            f.step = 'any';
            const fu = select([['1000', 'кГц'], ['1', 'Гц']], '1000');
            const depth = numberInput(root.dataset.depth || 16, 1);
            const ch = select([['1', 'моно (1)'], ['2', 'стерео (2)'], ['4', 'квадро (4)']], root.dataset.channels || '2');
            const t = numberInput(root.dataset.time || 60, 1);
            const tu = select([['1', 'секунд'], ['60', 'минут']], '1');
            const out = el('div');
            body.replaceChildren(append(el('div', 'w-controls'), field('Частота', append(el('span', 'w-range'), f, fu)),
                field('Разрядность, бит', depth), field('Каналы', ch), field('Время', append(el('span', 'w-range'), t, tu))), out);
            onInput([f, fu, depth, ch, t, tu], function () {
                out.replaceChildren();
                const F = +f.value * +fu.value;
                const B = Math.floor(+depth.value);
                const C = +ch.value;
                const T = +t.value * +tu.value;
                if (!(F > 0 && B > 0 && T > 0)) {
                    out.appendChild(message('Все величины должны быть положительными', 'warn'));
                    return;
                }
                const bits = F * B * C * T;
                const list = el('ol', 'w-eval');
                append(list,
                    el('li', null, 'Частота в герцах: ' + fmt(+f.value) + (fu.value === '1000' ? ' кГц = ' + fmt(F) + ' Гц' : ' Гц')
                        + ' — столько измерений в секунду'),
                    el('li', null, 'Время в секундах: ' + fmt(T) + ' с'),
                    el('li', null, 'Объём = частота · разрядность · каналы · время = ' + fmt(F) + ' · ' + B + ' · ' + C + ' · '
                        + fmt(T) + ' = ' + units(bits)));
                if (Number.isInteger(F) && Number.isInteger(T)) {
                    list.appendChild(el('li', null, 'Через степени двойки: ' + powers([F, B, C, T], bits)));
                }
                out.appendChild(list);
            });
        }

        function network() {
            const v = numberInput(root.dataset.volume || 5, 0);
            v.step = 'any';
            const vu = select([['8', 'байт'], ['8192', 'Кбайт'], ['8388608', 'Мбайт']], '8388608');
            const sp = numberInput(root.dataset.speed || 1048576, 1);
            const out = el('div');
            body.replaceChildren(append(el('div', 'w-controls'), field('Объём файла', append(el('span', 'w-range'), v, vu)),
                field('Скорость, бит/с', sp)), out);
            onInput([v, vu, sp], function () {
                out.replaceChildren();
                const bits = +v.value * +vu.value;
                const S = +sp.value;
                if (!(bits >= 0 && S > 0)) {
                    out.appendChild(message('Объём ≥ 0, скорость > 0', 'warn'));
                    return;
                }
                const sec = bits / S;
                const list = el('ol', 'w-eval');
                append(list, el('li', null, 'Объём в битах: ' + fmt(+v.value) + ' ' + vu.selectedOptions[0].textContent + ' = '
                    + fmt(bits) + ' бит' + (Number.isInteger(bits) && bits > 0 ? ' = ' + powerText(bits) : '')),
                el('li', null, 'Скорость: ' + fmt(S) + ' бит/с' + (Number.isInteger(S) ? ' = ' + powerText(S) : '')),
                el('li', null, 'Время = объём / скорость = ' + fmt(sec) + ' с' + (sec >= 60 ? ' = ' + fmt(sec / 60) + ' мин' : '')));
                out.appendChild(list);
            });
        }

        const modes = [[imgTab, image], [sndTab, sound], [netTab, network]];
        modes.forEach(function (m) {
            m[0].addEventListener('click', function () {
                modes.forEach(function (x) { x[0].classList.toggle('w-tab-on', x === m); });
                m[1]();
            });
        });
        const start = { image: 0, sound: 1, network: 2 }[root.dataset.mode || 'image'] || 0;
        modes[start][0].click();
    };

    // ---------- Растр: кликаем пиксели ----------
    widgets['pixel-grid'] = function (root) {
        frame(root, 'Растровая картинка своими руками');
        const size = Math.max(4, Math.min(+(root.dataset.size || 8), 12));
        const palette = select([['2', '2 цвета (1 бит на пиксель)'], ['4', '4 цвета (2 бита на пиксель)']], root.dataset.colors || '2');
        const grid = el('div', 'w-pixels w-pixels-' + size);
        const codes = el('pre', 'w-codes-out mono');
        const out = el('p', 'w-result');
        const clear = button('Очистить', 'w-button w-light');
        append(root, append(el('div', 'w-controls'), field('Палитра', palette), clear),
            el('p', 'hint', 'Нажимай на клетки, чтобы менять цвет пикселя. Справа — как картинка хранится в памяти.'),
            append(el('div', 'w-pixel-row'), grid, codes), out);
        // Начальный рисунок — смайлик для 8×8.
        const smile = ['00111100', '01000010', '10100101', '10000001', '10100101', '10011001', '01000010', '00111100'];
        const cells = [];
        for (let r = 0; r < size; r++) {
            for (let c = 0; c < size; c++) {
                const cell = button('', 'w-pixel');
                cell.dataset.v = size === 8 ? smile[r][c] : '0';
                cell.setAttribute('aria-label', 'пиксель ' + (r + 1) + ', ' + (c + 1));
                cell.addEventListener('click', function () {
                    cell.dataset.v = String((+cell.dataset.v + 1) % +palette.value);
                    render();
                });
                cells.push(cell);
                grid.appendChild(cell);
            }
        }
        function render() {
            const n = +palette.value;
            const bits = n === 2 ? 1 : 2;
            const lines = [];
            for (let r = 0; r < size; r++) {
                const row = [];
                for (let c = 0; c < size; c++) {
                    const cell = cells[r * size + c];
                    if (+cell.dataset.v >= n) {
                        cell.dataset.v = String(n - 1);
                    }
                    cell.className = 'w-pixel w-px-' + n + '-' + cell.dataset.v;
                    row.push((+cell.dataset.v).toString(2).padStart(bits, '0'));
                }
                lines.push(row.join(bits === 1 ? '' : ' '));
            }
            codes.textContent = lines.join('\n');
            const total = size * size * bits;
            out.textContent = size + ' · ' + size + ' = ' + size * size + ' пикселей × ' + bits + ' бит = ' + total
                + ' бит = ' + fmt(total / 8) + ' байт';
        }
        clear.addEventListener('click', function () {
            cells.forEach(function (c) { c.dataset.v = '0'; });
            render();
        });
        palette.addEventListener('change', render);
        render();
    };

    // ---------- Графы: общий разбор параметров ----------
    // nodes="А:60:40;Б:200:30" (имя:x:y), edges="АБ:7;БВ" (две вершины и необязательный вес).
    function parseGraph(root) {
        const nodes = {};
        const order = [];
        (root.dataset.nodes || '').split(';').forEach(function (part) {
            const m = /^\s*([^:\s]+)\s*:\s*(-?\d+)\s*:\s*(-?\d+)\s*$/.exec(part);
            if (m) {
                nodes[m[1]] = { x: +m[2], y: +m[3] };
                order.push(m[1]);
            }
        });
        const edges = [];
        (root.dataset.edges || '').split(';').forEach(function (part) {
            const t = part.trim();
            if (!t) {
                return;
            }
            const m = /^(.+?)(?::(\d+))?$/.exec(t);
            const pair = m[1];
            // Имена вершин — одна буква или цифра: «АБ», «A1»… Ищем разбиение на две известные вершины.
            for (let i = 1; i < pair.length; i++) {
                const a = pair.slice(0, i);
                const b = pair.slice(i);
                if (nodes[a] && nodes[b]) {
                    edges.push({ a: a, b: b, w: m[2] ? +m[2] : null });
                    break;
                }
            }
        });
        return { nodes: nodes, order: order, edges: edges };
    }

    function graphSvg(graph, directed, labels) {
        let maxX = 0;
        let maxY = 0;
        graph.order.forEach(function (n) {
            maxX = Math.max(maxX, graph.nodes[n].x);
            maxY = Math.max(maxY, graph.nodes[n].y);
        });
        const s = svg('svg', { viewBox: '0 0 ' + (maxX + 40) + ' ' + (maxY + 40), class: 'w-svg w-graph', role: 'img',
            'aria-label': 'Граф' });
        if (directed) {
            const defs = svg('defs');
            const marker = svg('marker', { id: 'w-arrow-' + Math.random().toString(36).slice(2), viewBox: '0 0 10 10',
                refX: 10, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' });
            marker.appendChild(svg('path', { d: 'M0 0 L10 5 L0 10 z', class: 'w-arrowhead' }));
            defs.appendChild(marker);
            s.appendChild(defs);
            s.dataset.marker = marker.id;
        }
        const edgeEls = graph.edges.map(function (e) {
            const a = graph.nodes[e.a];
            const b = graph.nodes[e.b];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const d = Math.sqrt(dx * dx + dy * dy) || 1;
            const r = 17;
            const line = svg('line', { x1: a.x + 20 + dx / d * r, y1: a.y + 20 + dy / d * r,
                x2: b.x + 20 - dx / d * (r + (directed ? 3 : 0)), y2: b.y + 20 - dy / d * (r + (directed ? 3 : 0)),
                class: 'w-gedge' });
            if (directed) {
                line.setAttribute('marker-end', 'url(#' + s.dataset.marker + ')');
            }
            s.appendChild(line);
            let label = null;
            if (e.w !== null && labels !== false) {
                label = svg('text', { x: (a.x + b.x) / 2 + 20 - dy / d * 10, y: (a.y + b.y) / 2 + 24 + dx / d * 10,
                    'text-anchor': 'middle', class: 'w-gweight' }, String(e.w));
                s.appendChild(label);
            }
            return { line: line, label: label };
        });
        const nodeEls = {};
        graph.order.forEach(function (n) {
            const p = graph.nodes[n];
            const g = svg('g', { class: 'w-gnode' });
            g.appendChild(svg('circle', { cx: p.x + 20, cy: p.y + 20, r: 16 }));
            g.appendChild(svg('text', { x: p.x + 20, y: p.y + 25, 'text-anchor': 'middle' }, n));
            s.appendChild(g);
            nodeEls[n] = g;
        });
        return { svg: s, edges: edgeEls, nodes: nodeEls };
    }

    // ---------- Граф ↔ таблица смежности ----------
    widgets['graph-table'] = function (root) {
        frame(root, 'Граф и таблица смежности');
        const graph = parseGraph(root);
        const pic = graphSvg(graph, false);
        const table = el('table', 'w-table w-adj');
        const head = el('tr');
        head.appendChild(el('th'));
        graph.order.forEach(function (n) { head.appendChild(el('th', null, n)); });
        head.appendChild(el('th', 'w-deg', 'степень'));
        table.appendChild(head);
        const cellOf = {};
        const degree = {};
        graph.order.forEach(function (n) { degree[n] = 0; });
        graph.edges.forEach(function (e) {
            degree[e.a]++;
            degree[e.b]++;
        });
        graph.order.forEach(function (r) {
            const tr = el('tr');
            tr.appendChild(el('th', null, r));
            graph.order.forEach(function (c) {
                const idx = graph.edges.findIndex(function (e) { return (e.a === r && e.b === c) || (e.a === c && e.b === r); });
                const td = el('td', idx >= 0 ? 'w-adj-on' : '', idx >= 0 ? (graph.edges[idx].w !== null ? String(graph.edges[idx].w) : '1') : '');
                if (r === c) {
                    td.classList.add('w-adj-diag');
                }
                if (idx >= 0) {
                    td.tabIndex = 0;
                    td.addEventListener('mouseenter', function () { highlight([idx]); });
                    td.addEventListener('focus', function () { highlight([idx]); });
                    td.addEventListener('click', function () { highlight([idx]); });
                }
                cellOf[r + '|' + c] = td;
                tr.appendChild(td);
            });
            tr.appendChild(el('td', 'w-deg', String(degree[r])));
            table.appendChild(tr);
        });
        const info = el('p', 'w-result', 'Наведи на дорогу в таблице или нажми на вершину на схеме.');
        const wrap = append(el('div', 'w-graph-row'), append(el('div', 'w-graph-pic'), pic.svg),
            append(el('div', 'w-scroll'), table));
        append(root, wrap, info);

        function highlight(indexes, node) {
            pic.edges.forEach(function (e, i) { e.line.classList.toggle('w-hl', indexes.indexOf(i) >= 0); });
            Object.keys(cellOf).forEach(function (k) { cellOf[k].classList.remove('w-hl'); });
            Object.keys(pic.nodes).forEach(function (n) { pic.nodes[n].classList.toggle('w-hl', n === node); });
            indexes.forEach(function (i) {
                const e = graph.edges[i];
                cellOf[e.a + '|' + e.b].classList.add('w-hl');
                cellOf[e.b + '|' + e.a].classList.add('w-hl');
            });
            if (node) {
                info.textContent = 'Вершина ' + node + ': степень ' + degree[node] + ' — из неё выходит ' + degree[node]
                    + ' ' + (degree[node] === 1 ? 'дорога' : degree[node] < 5 && degree[node] > 0 ? 'дороги' : 'дорог')
                    + '. В таблице это ' + degree[node] + ' заполненных клеток в строке ' + node + '.';
            } else if (indexes.length === 1) {
                const e = graph.edges[indexes[0]];
                info.textContent = 'Дорога ' + e.a + '–' + e.b + (e.w !== null ? ' длиной ' + e.w : '')
                    + ': в таблице она записана дважды — в строке ' + e.a + ' и в строке ' + e.b + '.';
            }
        }
        graph.order.forEach(function (n) {
            pic.nodes[n].addEventListener('click', function () {
                const idx = [];
                graph.edges.forEach(function (e, i) {
                    if (e.a === n || e.b === n) {
                        idx.push(i);
                    }
                });
                highlight(idx, n);
            });
        });
        pic.edges.forEach(function (e, i) {
            e.line.addEventListener('click', function () { highlight([i]); });
        });
    };

    // ---------- Количество путей в ориентированном графе ----------
    widgets['graph-paths'] = function (root) {
        frame(root, 'Подсчёт путей: подписываем вершины');
        const graph = parseGraph(root);
        const start = root.dataset.start || graph.order[0];
        const finish = root.dataset.end || graph.order[graph.order.length - 1];
        const pic = graphSvg(graph, true, false);
        const labels = {};
        graph.order.forEach(function (n) {
            const p = graph.nodes[n];
            const t = svg('text', { x: p.x + 40, y: p.y + 6, class: 'w-path-count' }, '');
            pic.svg.appendChild(t);
            labels[n] = t;
        });
        // Топологический порядок: вершина идёт после всех, из которых в неё ведут стрелки.
        const order = [];
        const done = {};
        let guard = 0;
        while (order.length < graph.order.length && guard++ < 1000) {
            graph.order.forEach(function (n) {
                if (done[n]) {
                    return;
                }
                const ins = graph.edges.filter(function (e) { return e.b === n; });
                if (ins.every(function (e) { return done[e.a]; })) {
                    order.push(n);
                    done[n] = true;
                }
            });
        }
        // Шаги — вершины по порядку до конечной включительно.
        const plan = order.slice(0, order.indexOf(finish) + 1);
        const counts = {};
        const explain = el('ol', 'w-eval');
        const next = button('Следующий шаг →');
        const all = button('Показать все шаги', 'w-button w-light');
        const reset = button('Сначала', 'link-button');
        const result = el('p', 'w-result');
        append(root, append(el('div', 'w-scroll'), pic.svg), append(el('div', 'steps-bar'), next, all, reset), explain, result);
        let step = 0;
        function apply(n) {
            const reach = graph.edges.filter(function (e) { return e.b === n && counts[e.a] > 0; });
            let text;
            if (n === start) {
                counts[n] = 1;
                text = n + ' — начало: сюда попадаем одним способом, пишем 1';
            } else {
                counts[n] = reach.reduce(function (s, e) { return s + counts[e.a]; }, 0);
                text = reach.length
                    ? n + ' = ' + reach.map(function (e) { return e.a; }).join(' + ') + ' = '
                    + reach.map(function (e) { return counts[e.a]; }).join(' + ') + ' = ' + counts[n]
                    : n + ': сюда не ведёт ни одной стрелки из уже подписанных вершин, пишем 0';
            }
            labels[n].textContent = String(counts[n]);
            pic.nodes[n].classList.add('w-done');
            Object.keys(pic.nodes).forEach(function (m) { pic.nodes[m].classList.toggle('w-hl', m === n); });
            pic.edges.forEach(function (e, i) { e.line.classList.toggle('w-hl', reach.indexOf(graph.edges[i]) >= 0); });
            explain.appendChild(el('li', null, text));
        }
        function update() {
            const finished = step >= plan.length;
            next.disabled = finished;
            all.hidden = finished;
            result.textContent = plan.length === 0 ? 'Конечная вершина недостижима: в графе есть цикл?'
                : finished ? 'Ответ: из ' + start + ' в ' + finish + ' ведёт путей: ' + counts[finish] + '.' : '';
        }
        next.addEventListener('click', function () {
            if (step < plan.length) {
                apply(plan[step++]);
            }
            update();
        });
        all.addEventListener('click', function () {
            while (step < plan.length) {
                apply(plan[step++]);
            }
            update();
        });
        reset.addEventListener('click', function () {
            step = 0;
            explain.replaceChildren();
            Object.keys(counts).forEach(function (k) { delete counts[k]; });
            graph.order.forEach(function (n) {
                labels[n].textContent = '';
                pic.nodes[n].classList.remove('w-done', 'w-hl');
            });
            pic.edges.forEach(function (e) { e.line.classList.remove('w-hl'); });
            update();
        });
        update();
    };

    // ---------- IP-адрес и маска ----------
    function parseIp(text) {
        const parts = (text || '').trim().split('.');
        if (parts.length !== 4 || parts.some(function (p) { return !/^\d{1,3}$/.test(p) || +p > 255; })) {
            return null;
        }
        return parts.map(Number);
    }

    widgets['ip-mask'] = function (root) {
        frame(root, 'IP-адрес и маска');
        const ip = input(root.dataset.ip || '192.168.37.200', { type: 'text', spellcheck: 'false' });
        const mask = input(root.dataset.mask || '255.255.240.0', { type: 'text', spellcheck: 'false' });
        ip.className = mask.className = 'mono';
        const out = el('div');
        append(root, append(el('div', 'w-controls'), field('IP-адрес', ip), field('Маска (или /20)', mask)), out);
        onInput([ip, mask], function () {
            out.replaceChildren();
            const a = parseIp(ip.value);
            let m;
            const pref = /^\s*\/?(\d{1,2})\s*$/.exec(mask.value);
            if (pref && +pref[1] <= 32) {
                const bits = '1'.repeat(+pref[1]).padEnd(32, '0');
                m = [0, 1, 2, 3].map(function (i) { return parseInt(bits.slice(i * 8, i * 8 + 8), 2); });
            } else {
                m = parseIp(mask.value);
            }
            if (!a) {
                out.appendChild(message('IP-адрес — четыре числа от 0 до 255 через точку', 'warn'));
                return;
            }
            if (!m) {
                out.appendChild(message('Маска — четыре числа от 0 до 255 через точку или /число', 'warn'));
                return;
            }
            const maskBits = m.map(function (x) { return x.toString(2).padStart(8, '0'); }).join('');
            if (!/^1*0*$/.test(maskBits)) {
                out.appendChild(message('Это не маска: в ней сначала должны идти только единицы, потом только нули. '
                    + 'Сейчас: ' + maskBits.replace(/(.{8})(?=.)/g, '$1.'), 'wrong'));
                return;
            }
            const ones = maskBits.indexOf('0') < 0 ? 32 : maskBits.indexOf('0');
            const net = a.map(function (x, i) { return x & m[i]; });
            const broadcast = a.map(function (x, i) { return (x & m[i]) | (~m[i] & 255); });
            const table = el('table', 'w-table w-ip');
            function row(name, bytes, cls) {
                const tr = el('tr', cls || '');
                tr.appendChild(el('th', null, name));
                bytes.forEach(function (byte, bi) {
                    const td = el('td', 'mono');
                    byte.toString(2).padStart(8, '0').split('').forEach(function (c, j) {
                        const pos = bi * 8 + j;
                        td.appendChild(span(pos < ones ? 'w-net' : 'w-host', c));
                        if (pos === ones - 1) {
                            td.appendChild(span('w-border', '|'));
                        }
                    });
                    tr.appendChild(td);
                });
                tr.appendChild(el('td', 'w-num', bytes.join('.')));
                table.appendChild(tr);
            }
            row('IP', a);
            row('Маска', m);
            row('Сеть = IP & маска', net, 'w-sum-row');
            const scroll = el('div', 'w-scroll');
            scroll.appendChild(table);
            const zeros = 32 - ones;
            const count = Math.pow(2, zeros);
            const list = el('ul', 'w-eval');
            append(list,
                el('li', null, 'Единиц в маске: ' + ones + ' (часть адреса сети), нулей: ' + zeros + ' (номер узла).'),
                el('li', null, 'Адрес сети: ' + net.join('.')),
                el('li', null, 'Всего адресов в сети: 2' + sup(zeros) + ' = ' + fmt(count)),
                el('li', null, 'Адресов для компьютеров (узлов): ' + (zeros >= 2 ? fmt(count) + ' − 2 = ' + fmt(count - 2)
                    : 'нет: при ' + zeros + ' нулях все адреса служебные')
                    + ' — первый адрес (сеть) и последний (' + broadcast.join('.') + ', широковещательный) заняты.'));
            append(out, scroll, el('p', 'hint', 'Синие биты — сеть, оранжевые — узел. Черта — граница маски.'), list);
        });
    };

    // ================= Запуск =================
    document.querySelectorAll('.widget[data-widget]').forEach(function (root) {
        const init = widgets[root.dataset.widget];
        if (!init) {
            return;
        }
        try {
            init(root);
        } catch (e) {
            root.replaceChildren(message('Виджет не запустился: ' + e.message, 'warn'));
        }
    });

    // Для тестов и отладки в консоли.
    window.CourseWidgets = { parse: parse, evaluate: evaluate, show: show, steps: steps };
})();
