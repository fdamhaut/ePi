import Image2Grid as i2g


class Board:

    def __init__(self, boardString="", size=(7, 6)):
        self.grid = [['E']*size[0] for i in range(size[1])]
        self.string = boardString
        self.successor = [0]*size[0]
        self.size = size
        self.lastmove = (0, 0)
        self.color = 'Y'

        for c in self.string:
            self.play(int(c))

    def play(self, num):
        if 0 > num or num >= self.size[0] or self.successor[num] >= self.size[1]:
            print('Error ', num, " is not a valid column")
            return False

        self.successor[num] += 1
        self.string += str(num)
        self.grid[-self.successor[num]][num] = self.color
        self.color = 'R' if self.color == 'Y' else 'Y'
        self.lastmove = (-self.successor[num], num)

    def play_cpy(self, num):
        cpy = Board(self.string)
        if cpy.play(num):
            return cpy
        return None

    def successor(self):
        for i in enumerate(self.size[0]):
            cpy = self.play_cpy(i)
            if cpy:
                yield cpy

    def __str__(self):
        strg = ""
        for l in self.grid:
            strg += str(l) + '\n'
        return strg

    def win(self):
        x, y = self.lastmove
        color = self.grid[x][y]
        return ((y < len(self.grid[0])-3 and self.grid[x][y+1] == color and self.grid[x][y+2] == color and self.grid[x][y+3] == color)          # Four in a row
                or (x < len(self.grid)-3 and (self.grid[x+1][y] == color and self.grid[x+2][y] == color and self.grid[x+3][y] == color)         # Four in a column
                or (y < len(self.grid[0])-3 and self.grid[x+1][y+1] == color and self.grid[x+2][y+2] == color and self.grid[x+3][y+3] == color) # Four in a descreasing diagonal
                or (y > 2 and self.grid[x+1][y-1] == color and self.grid[x+2][y-2] == color and self.grid[x+3][y-3] == color)))                 # Four in an inscreasing diagonal

    def heuristic(self):
        if self.win():
            return 1
        return 0


def negamax(board, depth, color):
    if depth == 0 or board.win():
        return color * board.heuristic()

    value = float('-inf')

    for b in board.successor():
        value = max(value, -negamax(b, depth-1, -color))

    return value

