a, b, k = map(int, input().split())
cnt = 0
for x in range(a, b + 1):
    if x % k == 0:
        cnt += 1
print(cnt)
