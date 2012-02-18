all: rectangle_detector_main
	
clean:
	rm -f rectangle_detector_main
	
	
rectangle_detector_main: rectangle_detector_main.cpp util.hpp util_debug.cpp rectangle_detector.h
	g++ rectangle_detector_main.cpp -pg -Wall -g -o rectangle_detector_main `pkg-config opencv --cflags --libs`
	
