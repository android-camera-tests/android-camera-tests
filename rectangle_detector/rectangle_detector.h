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
float configPaperDetectorMinRectangleArea = 25.0;
float configPaperDetectorMaxRectangleArea = 320*320;
float configPaperDetectorMaxShapeHuDiff = 0.01;
float configPaperDetectorAdaptiveThresKernelSize = 31;
float configPaperDetectorBinarizerOffset = -2.0;
int configMaxRectsToDetect = 100;

static inline float sqr(float x) {return x*x;}

typedef struct {
	Point2f boundingRectCorners[4]; ///Rotated bounding rect corners
	float epsilon; //Goodness value
	Point2f subpixelCorners[4]; ///Estimated subpixel corners
	float subpixelCornersArea; ///Area from subpixel corners
	float boundingRectArea; ///Area from subpixel corners
	float contourArea;
	float huDiff;
} detected_rectangle_t;

#define get_no_rects_from_size(floatArrayLength) ((floatArrayLength) * sizeof(float) / sizeof(detected_rectangle_t))


#define ct_assert(e) extern char (*ct_assert_x_(void)) [sizeof(char[1 - 2*!(e)])]
ct_assert(sizeof(detected_rectangle_t) == sizeof(float)*21);


static void insertRectangle(float* out_rects,int maxRects,int& rects_count, const detected_rectangle_t& newRect) {
	detected_rectangle_t* rect_data = (detected_rectangle_t*)out_rects;
	int insertPos = rects_count;
	for (int i=0;i<rects_count;i++) {
		if (newRect.epsilon < rect_data[i].epsilon) {
			insertPos = i;
			break;
		}
	}
	if (rects_count+1 < maxRects) {
		memmove(&rect_data[insertPos+1],&rect_data[insertPos],sizeof(*rect_data)*(rects_count-insertPos));
		rects_count++;
	}
	if (insertPos < maxRects) {
		rect_data[insertPos] = newRect;
	}
}


/**
 * Calculate the "probabilty" of a list of rects that are papers:
 *
 * Rectangle
 * Roughly size of a letter or A4 paper
 * Angled 0 or 90 degrees
 * White
 *
 *
 */
int detect_rectangles1(const Mat& img_gray, float* out_rects, int& out_rects_count, Mat* plotImage = 0)
{
	Mat dst_binary;
	vector<vector<Point2i> > contours;
	int numberOfRects = 0;
	int max_rects = out_rects_count;
	out_rects_count = 0;

	adaptiveThreshold(img_gray,dst_binary,255,ADAPTIVE_THRESH_GAUSSIAN_C ,THRESH_BINARY,configPaperDetectorAdaptiveThresKernelSize,configPaperDetectorBinarizerOffset);
	//erode(dst_binary,dst_binary,Mat());

	Mat erodeKernel(3,3,CV_8U,Scalar(255));

	morphologyEx(dst_binary,dst_binary,MORPH_ERODE,erodeKernel);
	findContours(dst_binary,contours,CV_RETR_EXTERNAL,CV_CHAIN_APPROX_SIMPLE);
	//CV_RETR_LIST
	vector<Point2i> referenceContour;
	referenceContour.push_back(Point(0,0));
	referenceContour.push_back(Point(85,0));
	referenceContour.push_back(Point(85,110));
	referenceContour.push_back(Point(0,110));
	//float imgArea = img_gray.cols * img_gray.rows;

	for (unsigned int k=0;k<contours.size();k++) {
		detected_rectangle_t detectedRectangle;
		detectedRectangle.contourArea = contourArea(contours[k]);
		if (configPaperDetectorMinRectangleArea < detectedRectangle.contourArea && detectedRectangle.contourArea < configPaperDetectorMaxRectangleArea) {
			RotatedRect rotRect = minAreaRect(contours[k]);
			detectedRectangle.huDiff = matchShapes(contours[k],referenceContour,3,0);
			detectedRectangle.boundingRectArea = rotRect.size.height * rotRect.size.width;
			if (detectedRectangle.huDiff / detectedRectangle.boundingRectArea < configPaperDetectorMaxShapeHuDiff) {
				if (numberOfRects < configMaxRectsToDetect) {
					detectedRectangle.boundingRectArea = rotRect.size.height * rotRect.size.width;
					//int circumferance = rotRect.size.width + rotRect.size.height;
					//Rect subAreaRect(Point(rotRect.center.x-circumferance,rotRect.center.y-circumferance),Point(rotRect.center.x+circumferance,rotRect.center.y+circumferance));
					//Mat subAreaImg(img_gray,subAreaRect); //TODO fix, can throw because out of bounds?
					std::vector<Point2f> pointsOrig(4);
					std::vector<Point2f> points(4);
					rotRect.points(detectedRectangle.boundingRectCorners);
					rotRect.points(detectedRectangle.subpixelCorners);
					//cornerSubPix(img_gray,detectedRectangle.subpixelCorners,Size(3,3),Size(0,0),TermCriteria(TermCriteria::EPS,20,0.1));

					detectedRectangle.subpixelCornersArea = area_of_rectangle(detectedRectangle.subpixelCorners);

					int value = 0;
					value += dst_binary.at<uchar>(points[0].x,points[0].y);
					value += dst_binary.at<uchar>(points[1].x,points[1].y);
					value += dst_binary.at<uchar>(points[2].x,points[2].y);
					value += dst_binary.at<uchar>(points[3].x,points[3].y);

					int angle = rotRect.angle;
					angle = (angle + 45) % 90 - 45;

					//TODO add error contribution  for rotation not 0,90,180,270
					detectedRectangle.epsilon = sqrt(
							sqr(detectedRectangle.huDiff) +
							sqr(detectedRectangle.boundingRectArea - detectedRectangle.contourArea) / sqr(detectedRectangle.contourArea) +
							(sqr(rotRect.size.height - rotRect.size.width) / (detectedRectangle.boundingRectArea * 10)) );

					insertRectangle(out_rects,max_rects,out_rects_count,detectedRectangle);
				}
				numberOfRects++;
			}
			else {
			}
		}

	}
	if (plotImage != 0) {
		for (unsigned int k=0;k<contours.size();k++) {
			drawContours(*plotImage,contours,k,Scalar(0,100,200,128),3);
			drawContours(*plotImage,contours,k,Scalar(0,140,255,255),1);
		}
		detected_rectangle_t* rects_p = (detected_rectangle_t*)out_rects;
		for (unsigned int k=out_rects_count;k-->0;) {
			detected_rectangle_t& detectedRectangle = rects_p[k];
			char buf[200];
			sprintf(buf,"%d:%.3f",k+1,detectedRectangle.epsilon);
			Scalar lineColor = Scalar(255,100,100,100);
			if (k==0) {
				lineColor = Scalar(255,255,100,100);
			}
			line(*plotImage,detectedRectangle.boundingRectCorners[0],detectedRectangle.boundingRectCorners[1],lineColor,2);
			line(*plotImage,detectedRectangle.boundingRectCorners[1],detectedRectangle.boundingRectCorners[2],lineColor,2);
			line(*plotImage,detectedRectangle.boundingRectCorners[2],detectedRectangle.boundingRectCorners[3],lineColor,2);
			line(*plotImage,detectedRectangle.boundingRectCorners[3],detectedRectangle.boundingRectCorners[0],lineColor,2);
			Point2f center(0.0f,0.0f);
			center += detectedRectangle.boundingRectCorners[0];
			center += detectedRectangle.boundingRectCorners[1];
			center += detectedRectangle.boundingRectCorners[2];
			center += detectedRectangle.boundingRectCorners[3];
			center *= 0.25f;
			putText(*plotImage,buf,center,FONT_HERSHEY_PLAIN,1.0,Scalar(0,0,0,200),3);
			putText(*plotImage,buf,center,FONT_HERSHEY_PLAIN,1.0,Scalar(200,200,200,200),1);
		}
	}

	return out_rects_count;
}



int detect_rectangles2(const Mat& img_gray, vector<Vec8f>& out_rects,Mat* plotImage = 0)
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
				line(*plotImage,start,end,Scalar(128,0,100,128),1);
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

