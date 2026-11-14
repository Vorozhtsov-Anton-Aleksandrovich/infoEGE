from itertools import permutations

word = input()
vowels = 'АЕЁИОУЫЭЮЯ'
res = set()
for p in permutations(word):
    w = ''.join(p)
    ok = True
    for i in range(len(w) - 1):
        if w[i] in vowels and w[i + 1] in vowels:
            ok = False
    if ok:
        res.add(w)
print(len(res))
