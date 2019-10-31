#include <cmath>
#include <pybind11/pybind11.h>

namespace py = pybind11;


double fun56() {
	return 56;
}

double funadd56(double x) {
	return x + 56;
}


PYBIND11_MODULE(test56, m) {
	m.def("fun56", &fun56, R"pbdoc(
		return 56.
    )pbdoc");

#ifdef VERSION_INFO
	m.attr("__version__") = VERSION_INFO;
#else
	m.attr("__version__") = "dev";
#endif
}