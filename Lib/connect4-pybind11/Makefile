c4solver: main.o Solver.o
	c++ -O3 -Wall -shared -std=c++11 -fPIC `python3 -m pybind11 --includes` main.o Solver.o  -o c4solver`python3-config --extension-suffix`

main.o:
	c++ -O3 -Wall -shared -std=c++11 -fPIC `python3 -m pybind11 --includes` -c main.cpp -o main.o
	
Solver.o:
	c++ -O3 -Wall -shared -std=c++11 -fPIC `python3 -m pybind11 --includes` -c Solver.cpp -o Solver.o

clean:
	rm -f *.o c4solver.*
