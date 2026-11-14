M = int(input())
N = 1
while True:
    s = bin(N)[2:]
    s += str(s.count('1') % 2)
    s += str(s.count('1') % 2)
    if int(s, 2) > M:
        print(N)
        break
    N += 1
