def divisors(n):
    res = []
    d = 1
    while d * d <= n:
        if n % d == 0:
            res.append(d)
            if d != n // d:
                res.append(n // d)
        d += 1
    return sorted(res)

a, b = map(int, input().split())
for x in range(a, b + 1):
    ds = divisors(x)
    if len(ds) == 4:
        print(x, ds[-2])
