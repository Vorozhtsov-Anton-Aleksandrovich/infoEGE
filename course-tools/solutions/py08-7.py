a = list(map(int, input().split()))
cnt = 0
best = None
for i in range(len(a) - 1):
    if a[i] % 3 == 0 or a[i + 1] % 3 == 0:
        cnt += 1
        s = a[i] + a[i + 1]
        if best is None or s > best:
            best = s
print(cnt, best if best is not None else 0)
