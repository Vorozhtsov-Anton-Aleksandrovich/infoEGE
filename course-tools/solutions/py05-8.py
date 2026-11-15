n = int(input())
cnt = 0
s = 0
for i in range(n):
    x = int(input())
    if x % 3 == 0:
        cnt += 1
        s += x
print(cnt, s)
