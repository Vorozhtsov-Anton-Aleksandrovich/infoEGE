n, b = map(int, input().split())
digits = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
if n == 0:
    print(0)
else:
    s = ''
    while n > 0:
        s = digits[n % b] + s
        n //= b
    print(s)
