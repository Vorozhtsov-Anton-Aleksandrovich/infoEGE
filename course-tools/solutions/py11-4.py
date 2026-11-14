from itertools import product

letters, k = input().split()
k = int(k)
cnt = 0
for w in product(letters, repeat=k):
    ok = True
    for i in range(k - 1):
        if w[i] == w[i + 1]:
            ok = False
            break
    if ok:
        cnt += 1
print(cnt)
