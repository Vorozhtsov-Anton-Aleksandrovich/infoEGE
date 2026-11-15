n, b, d = map(int, input().split())
if n == 0:
    print(1 if d == 0 else 0)
else:
    cnt = 0
    while n > 0:
        if n % b == d:
            cnt += 1
        n //= b
    print(cnt)
