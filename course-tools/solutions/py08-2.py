a = list(map(int, input().split()))
s = sum(a)
n = len(a)
cnt = 0
for x in a:
    if x * n > s:
        cnt += 1
print(cnt)
