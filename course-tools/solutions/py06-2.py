n = int(input())
cnt = 1
while n >= 10:
    n //= 10
    cnt += 1
print(cnt)
