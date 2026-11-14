from functools import lru_cache

@lru_cache(None)
def F(n):
    if n <= 2:
        return n
    return F(n - 1) + 2 * F(n - 2)

print(F(int(input())))
