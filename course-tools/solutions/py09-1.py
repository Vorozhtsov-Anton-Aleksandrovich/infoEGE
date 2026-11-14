def is_prime(n):
    if n < 2:
        return False
    d = 2
    while d * d <= n:
        if n % d == 0:
            return False
        d += 1
    return True

a, b = map(int, input().split())
print(sum(1 for x in range(a, b + 1) if is_prime(x)))
