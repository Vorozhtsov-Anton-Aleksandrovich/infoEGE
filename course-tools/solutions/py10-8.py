p, q = map(int, input().split())
for A in range(1000, 0, -1):
    if all(x % A == 0 or x % p != 0 or x % q != 0 for x in range(1, 1001)):
        print(A)
        break
