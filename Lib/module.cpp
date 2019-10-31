#include <pybind11/pybind11.h>

namespace py = pybind11;


int add(int i, int j) {
    return i + j;
}

int retsum56(int k) {
	return k+56;
}

PYBIND11_MODULE(addition, m) {
    m.doc() = "pybind11 addition"; // optional module docstring

    m.def("add", &add, "A function which adds two numbers");
	
	m.def("sum56", &retsum56, "add 56");
}