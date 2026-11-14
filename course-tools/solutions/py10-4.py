f = open('26.txt')
S, N = map(int, f.readline().split())
a = sorted(int(f.readline()) for i in range(N))
k = 0
total = 0
while k < N and total + a[k] <= S:
    total += a[k]
    k += 1
base = total - a[k - 1]
best = a[k - 1]
for j in range(k, N):
    if base + a[j] <= S:
        best = a[j]
print(k, best)
