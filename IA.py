import Image2Grid as i2g
from copy import copy
import pickle


def load(grid, player, table):
    position, mask = '', ''
    turn = 0
    col = [0] * 7
    # Start with right-most column
    for j in range(6, -1, -1):
        # Add 0-bits to sentinel
        mask += '0'
        position += '0'
        # Start with bottom row
        for i in range(0, 6):
            mask += ['0', '1'][grid[i][j] != "E"]
            turn += [0, 1][grid[i][j] != "E"]
            col[j] = [col[j], 7-i][grid[i][j] != "E"]
            position += ['0', '1'][grid[i][j] == player]
    print(col)
    return Board(int(position, 2), int(mask, 2), col, table, turn, 1)


def negamax(board):
    if board.stop():
        return board.positive * board.value(), 0

    val = float('-inf')
    path = 0
    for i, b in board.successor():
        print(i)
        if b.stop():
            v = b.value()
            if v > val:
                val = v
                path = i

    '''
    for i, b in board.successor():
        v, p = negamax(b)
        v = -v
        if v > val:
            val = v
            path = i
            if val > 0:
                break
    '''
    if board.turn > 8 and board.turn % 8 == 0:
        board.table[board.key()] = val
    return val, path


def ia(grid, player):
    table = loadTable()
    board = load(grid, player, table)
    val, path = negamax(board)
    yield (path, val)
    saveTable(table)


class Board:
    def __init__(self, position, mask, col, table, turn, positive):
        self.position = position
        self.mask = mask
        self.col = col
        self.table = table
        self.turn = turn
        self.position = self.position ^ self.mask
        self.positive = positive

    def play(self, num):
        if self.col[num] > 5:
            return False
        self.position = self.position ^ self.mask
        self.mask = self.mask | (self.mask + (1 << (self.col[num] * 7)))
        self.col[num] += 1
        self.turn += 1
        self.positive = -self.positive
        return True

    def play_cpy(self, num):
        cpy = Board(self.position, self.mask, copy(self.col), self.table, self.turn, self.positive)
        if cpy.play(num):
            return cpy
        return None

    def successor(self):
        for i in [2, 4, 3, 5, 1, 6, 0]:
            cpy = self.play_cpy(i)
            if cpy:
                yield (i, cpy)

    def key(self):
        return int('{0:048b}'.format(self.position) + '{0:048b}'.format(self.mask), 2)

    def stop(self):
        return self.turn >= 42 or self.key() in self.table or self.win()

    def value(self):
        if self.key() in self.table:
            return self.table[self.key()]
        if self.win():
            return 42-self.turn
        return 0

    def win(self):
        # Horizontal check
        m = self.position & (self.position >> 7)
        if m & (m >> 14):
            return True
        # Diagonal \
        m = self.position & (self.position >> 6)
        if m & (m >> 12):
            return True
        # Diagonal /
        m = self.position & (self.position >> 8)
        if m & (m >> 16):
            return True
        # Vertical
        m = self.position & (self.position >> 1)
        if m & (m >> 2):
            return True
        # Nothing found
        return False

def saveTable(table):
    with open('table.pkl', 'wb') as f:
        pickle.dump(table, f, pickle.HIGHEST_PROTOCOL)

def loadTable():
    with open('table.pkl', 'rb') as f:
        return pickle.load(f)


def importTable():
    f = open("connect-4.data")
    table = dict()

    resulttable = {
        "win\n": 1,
        "loss\n": -1,
        "draw\n": 0
    }

    for i in range(67557):
        line = f.readline()
        line = line.split(",")
        result = line[-1]
        pos = ""
        mask = ""
        for j in range(6, -1, -1):
            mask += "0"
            pos += "0"
            for k in range(6, -1, -1):
                l = line[j*6+k]
                pos += ["0", "1"][l == "x"]
                mask += ["1", "0"][l == "b"]
        table[int(pos + mask, 2)] = resulttable[result]
        pos = int(pos, 2) ^ int(mask, 2)
        pos = '{0:048b}'.format(pos)
        table[int(pos + mask, 2)] = resulttable[result]
    saveTable(table)


if __name__ == "__main__":
    grid = [['E', 'E', 'E', 'E', 'E', 'E', 'E'], ['E', 'E', 'E', 'E', 'E', 'E', 'E'], ['E', 'E', 'E', 'E', 'E', 'E', 'E'], ['E', 'E', 'E', 'E', 'E', 'E', 'E'], ['E', 'E', 'R', 'R', 'R', 'E', 'E'], ['E', 'E', 'Y', 'Y', 'Y', 'E', 'E']]

    for l in grid:
        print(l)

    for (p, v) in ia(grid, "Y"):
        print(p, v)

