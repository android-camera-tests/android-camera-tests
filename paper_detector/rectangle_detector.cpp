/*
 * rectangle_detector.cpp
 *
 *  Created on: Jan 25, 2012
 *      Author: birkler
 */


#include "util.hpp"

using namespace cv;
using namespace std;

int configMaxCornersPaperDetector = 100;

int detect_rectangles(const Mat& img_gray, vector<Vec8f>& out_rects,Mat* plotImage = 0)
{
	  // Detect the corners, internally this is a corner detector which keep a goodness value of each of the corners
	  // we limit the number of corners so not to kill the tesselator
	  ///////////////////////////////////
	  Mat corners;
	  goodFeaturesToTrack(img_gray,corners,configMaxCornersPaperDetector,0.08,9,noArray(),5,0,0.04);

	  /// Output corners if caller wants to
	  if (plotImage != 0) {
		  for (int i=0;i < corners.rows;i++) {
			  Point2f p = corners.at<Point2f>(i,0);
			   circle(*plotImage, p, 5, Scalar(255,0,100,100),2);
		   }
	  }

	  //Quit early if no corners
	  if (corners.rows <=0) return 0;


	  /// Connect the dots... this is potentially O(n^2) so be careful with number of corners
	  ////////////////////////////////////////////////////////
	  Rect boundingBox(0,0,img_gray.cols,img_gray.rows);
	  Subdiv2D voronioTesselatorSrc(boundingBox);
	  voronioTesselatorSrc.insert(corners);
	  std::vector<Vec4f> edgeSrcList;
	  voronioTesselatorSrc.getEdgeList(edgeSrcList);


	  /// Output triangles if caller wants to
	  if (plotImage != 0) {
		  for (vector<Vec4f>::iterator it = edgeSrcList.begin(); it!=edgeSrcList.end(); ++it) {
			  Point start = Point((*it)[0], (*it)[1]);
			  Point end = Point((*it)[2],(*it)[3]);
			  if (start.inside(boundingBox) && end.inside(boundingBox)) {
				  line(*plotImage,start,end,Scalar(128,0,100,100),1);
			  }
		  }
	  }

	  /// Inspect the triangles to find any right angled triangles
	  // then if we find a right angled triangle see if there is a matching
	  // rectangle
	  ////////////////////////////////////////////////////////

	  int numOfRectangles = 0;

	  std::vector<Vec6f> triangleSrcList;
	  voronioTesselatorSrc.getTriangleList(triangleSrcList);

	  for (vector<Vec6f>::iterator it = triangleSrcList.begin(); it!=triangleSrcList.end(); ++it) {

		  Point2f a = Point2f((*it)[0], (*it)[1]);
		  Point2f b = Point2f((*it)[2],(*it)[3]);
		  Point2f c = Point2f((*it)[4],(*it)[5]);
		  //Ok first prune rectangles from the seed triangles
		  if (boundingBox.contains(a) && boundingBox.contains(b) && boundingBox.contains(c)) {
			  Scalar color = Scalar(255,200,100,100);
			  int width = 1;
			  //Create the vectors for triangle
			  Point2f v1 = b - a;
			  Point2f v2 = c - a;
			  Point2f v3 = c - b;
			  Point2f d; //Expected point for rectangle point

			  //For a right angles triangle pythogoras holds:
			  //z = sqrt(x^2 + y^2) => z^2 = x^2 + y^2  =>  if (x^2 + x^2 - z^2 < epsilon) => right angled

			  //Into vectors:
			  //z=magnitude(largest) x=magnitude(v1) y=magnitude(v2)
			  //

			  float m1 = magnitude_sqr(v1);
			  float m2 = magnitude_sqr(v2);
			  float m3 = magnitude_sqr(v3);
			  float epsilon;
			  float area_calc = signed_area_of_triangle(a,b,c);

			  if (m1 > m2) {
				  if (m1 > m3) {
					  epsilon = abs(m1-m2-m3);
					  d = a + b - c;
				  }
				  else {
					  epsilon = abs(m3-m2-m1);
					  d = c + b - a;
				  }
			  } else {
				  if (m2 > m3) {
					  epsilon = abs(m2-m1-m3);
					  d = a + c - b;
				  }
				  else {
					  epsilon = abs(m3-m2-m1);
					  d = c + b - a;
				  }
			  }


			  if (epsilon < 0.1 * abs(area_calc)) {
				  //a-b and a-c seems perpendicular
				  //Do we have a point at d?
				  //first we check if de is even within the bounding rectangle otherwise the findNearest will throw
				  if (d.inside(boundingBox)) {
					  Point2f e;
					  voronioTesselatorSrc.findNearest(d,&e);
					  color = Scalar(255,50,200,100);

					  width = 3;
					  if (magnitude_sqr(d-e) < 9.0) {
						  color = Scalar(55,50,200,100);
						  //A rectangle!!
						 width =5;
						numOfRectangles++;
						Vec8f rect(a.x,a.y,b.x,b.y,c.x,c.y,d.x,d.y);
						out_rects.push_back(rect);
					  }
				  }
			  }
			  /// Output right angled triangle if caller wants to
			  if (plotImage != 0) {
				  line(*plotImage,a,b,color,width);
				  line(*plotImage,a,c,color,width);
				  line(*plotImage,b,c,color,width);
			  }
		  }
	  }
	  return numOfRectangles;
}

