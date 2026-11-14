n = int(input())
s = '1' * n
while '11' in s:
    s = s.replace('11', '2', 1)
print(s)
