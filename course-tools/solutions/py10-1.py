a = [int(x) for x in open('17.txt')]
cnt = 0
best = None
for i in range(len(a) - 1):
    if (a[i] % 2 == 0) != (a[i + 1] % 2 == 0):
        cnt += 1
        s = a[i] + a[i + 1]
        if best is None or s > best:
            best = s
print(cnt, best if best is not None else 0)
