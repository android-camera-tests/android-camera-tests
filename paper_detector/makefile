all: paper_detector
	

paper_detector: paper_detector.cpp util.hpp util_debug.cpp rectangle_detector.cpp
	g++ paper_detector.cpp -pg -Wall -g -o paper_detector `pkg-config opencv --cflags --libs`
	
