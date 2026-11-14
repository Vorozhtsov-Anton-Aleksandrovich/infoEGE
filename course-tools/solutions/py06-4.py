n = int(input())
ok = n > 1
d = 2
while d * d <= n:
    if n % d == 0:
        ok = False
        break
    d += 1
print('YES' if ok else 'NO')
