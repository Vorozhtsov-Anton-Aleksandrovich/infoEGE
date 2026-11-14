s = input()
cur = best = 1
for i in range(1, len(s)):
    if s[i] != s[i - 1]:
        cur += 1
    else:
        cur = 1
    best = max(best, cur)
print(best)
