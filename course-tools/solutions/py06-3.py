s = input()
cnt = 0
for c in s.lower():
    if c in 'aeiou':
        cnt += 1
print(cnt)
