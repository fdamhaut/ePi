f = open("connect-4.data")
table = dict()

resulttable ={
    "win\n":1,
    "loss\n":-1,
    "draw\n":0
}

for i in range(67557):
    line = f.readline()
    line = line.split(",")
    result = line[-1]
    pos = ""
    mask = ""
    for i in range(5, -1, -1):
        mask += "0"
        pos += "0"
        for j in range(0, 7):
            l = line[j*6+i]
            pos += ["0", "1"][l == "x"]
            mask += ["1", "0"][l == "b"]

    table[int(pos+mask, 2)] = resulttable[result]
    pos = int(pos, 2) ^ int(mask, 2)
    pos = '{0:048b}'.format(pos)
    table[int(pos + mask, 2)] = resulttable[result]

print(len(table))
