n = int(input())
a = [int(input()) for i in range(n)]
a.sort(reverse=True)
print(a[0], a[1], a[2])
