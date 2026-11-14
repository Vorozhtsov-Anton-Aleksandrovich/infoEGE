from itertools import product

for x, y, z, w in product([0, 1], repeat=4):
    F = ((not x or y) and not z) or w
    if not F:
        print(x, y, z, w)
