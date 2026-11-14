s = open('24.txt').readline().strip()
cur = best = 0
for c in s:
    if c.isdigit():
        cur += 1
        best = max(best, cur)
    else:
        cur = 0
print(best)
