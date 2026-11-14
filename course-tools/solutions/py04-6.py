x, y, z = map(int, input().split())
F = (not x or y) and (y == (not z))
print(int(F))
