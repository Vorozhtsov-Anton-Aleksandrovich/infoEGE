cnt = 0
for line in open('9.txt'):
    a = sorted(map(int, line.split()))
    if len(set(a)) == 4 and a[0] + a[3] > a[1] + a[2]:
        cnt += 1
print(cnt)
