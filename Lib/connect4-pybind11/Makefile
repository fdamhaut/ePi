CXX=c++
CXXFLAGS=--std=c++11 -W -Wall -O3 -shared -fPIC `python3 -m pybind11 --includes`

SRCS=Solver.cpp
OBJS=$(subst .cpp,.o,$(SRCS))

c4solver:
	$(CXX) $(LDFLAGS) $(CXXFLAGS) -o c4solver.`python3-config --extension-suffix` main.cpp $(LOADLIBES) $(LDLIBS)

.depend: $(SRCS)
	$(CXX) $(CXXFLAGS) -MM $^ > ./.depend
	
-include .depend

clean:
	rm -f *.o .depend c4solver generator


