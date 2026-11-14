def F(n):
    if n == 1:
        return 1
    return F(n - 1) + 2 * n

print(F(int(input())))
