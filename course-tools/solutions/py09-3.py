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

print(*divisors(int(input())))
