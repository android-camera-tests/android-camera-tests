/**
 * Paper detector; finding a piece of white letter paper on a wall
 *
 * 1. Enhance contrast by expanding between 65% and 99% percentile of the histogram.
 * 2. Detect features from refence
 *
 *
 * @function cornerDetector_Demo.cpp
 * @brief Demo code for detecting corners using OpenCV built-in functions
 * @author Jorgen Birkler
 */
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/features2d/features2d.hpp"
#include "opencv2/flann/flann.hpp"

#include <iostream>
#include <stdio.h>
#include <stdlib.h>

using namespace cv;
using namespace std;
using namespace cv::flann;


#include "util.hpp"
#include "util_debug.cpp"
#include "rectangle_detector.cpp"


/// Global variables
Mat myHarris_dst; Mat myHarris_copy; Mat Mc;
Mat myShiTomasi_dst; Mat myShiTomasi_copy;

int myShiTomasi_qualityLevel = 50;
int myHarris_qualityLevel = 50;
int max_qualityLevel = 100;

double myHarris_minVal; double myHarris_maxVal;
double myShiTomasi_minVal; double myShiTomasi_maxVal;

RNG rng(12345);

const char* myHarris_window = "My Harris corner detector";
const char* myShiTomasi_window = "My Shi Tomasi corner detector";
const char* myHistEqu_window = "Hist Equ";
const char* myReferenceImage_window = "Reference paper image with keypoints";



float configBlackPercentileForRoom = 0.65f;
float configWhitePercentileForWhitePaperInRoom = 1.0f/4000.0f; //PAper ~20x20 pixels in VGA=>400/400000 = 1/4000
int configMaxConerPaperDetector = 100;



/// Function headers
void myShiTomasi_function( int, void* );



/**
 * @function main
 */
int main( int argc, char** argv )
{
  /// Load source image and convert it to gray
  Mat src_orig = imread( argv[1], 1 );
  Mat src_reference_orig = imread( argv[2], 1 );
  Mat src_gray;
  //src_orig = Mat(src_orig,Range(100,480),Range(100,550));
  Mat src;
  vector<vector<Point2i> > contours;
  Mat erodeKernel = Mat(3,3,CV_32SC1);
  int no_rectangles = 10;
  detected_rectangle_t rectangles[no_rectangles];

  for (int i=0;i<1;i++) {
	  src = src_orig.clone();
	  //stretchContrastFromHistogram(src_orig,src,configBlackPercentileForRoom,configWhitePercentileForWhitePaperInRoom);
	  cvtColor( src, src_gray, CV_BGR2GRAY );
	  detect_rectangles1(src_gray,(float*)&rectangles[0],no_rectangles,&src);
  }

  for (int j=0;j<no_rectangles;j++) {
	  float* pRect = (float*)&rectangles[j].boundingRectCorners[0].x;
	  int top=src_orig.rows;
	  int bottom = 0;
	  int left = src_orig.cols;
	  int right = 0;
	  for (int l=0;l<8;l++) {
		  if (l&0x1) {
			  right = std::max(right,(int)pRect[l]);
			  left = std::min(left,(int)pRect[l]);
		  } else {
			  bottom = std::max(bottom,(int)pRect[l]);
			  top = std::min(bottom,(int)pRect[l]);
		  }
	  }

	  /*
	  Mat img = Mat(src_orig,Rect(Point(left,top),Point(right+1,bottom+1)));
	  Mat scaled;
	  resize(img,scaled,Size((right-left)*8,(bottom-top)*8));
	  */

	  const char* myCorner_window = "Corner";
	  namedWindow( myCorner_window, CV_WINDOW_AUTOSIZE );
	  //imshow( myCorner_window, scaled );

	  line(src,Point(pRect[0],pRect[1]),Point(pRect[2],pRect[3]),Scalar(255,0,100,100),2);
	  line(src,Point(pRect[2],pRect[3]),Point(pRect[4],pRect[5]),Scalar(255,0,100,100),2);
	  line(src,Point(pRect[4],pRect[5]),Point(pRect[6],pRect[7]),Scalar(255,0,100,100),2);
	  line(src,Point(pRect[0],pRect[1]),Point(pRect[6],pRect[7]),Scalar(255,0,100,100),2);
  }
  //detect_rectangles(src_gray,rectangles,&src_orig);


  //Debug:
  const char* myInputImage_window = "Input image";
  const char* myInputImageContrastStretched_window = "Contrast stretched input";
  namedWindow( myInputImage_window, CV_WINDOW_AUTOSIZE );
  imshow( myInputImage_window, src );
  plotHistogramToNewWindow(Histogram(src_orig),myInputImage_window);
  namedWindow( myInputImageContrastStretched_window, CV_WINDOW_AUTOSIZE );
  imshow( myInputImageContrastStretched_window, src );
  plotHistogramToNewWindow(Histogram(src),myInputImageContrastStretched_window);
  const char* myRectangleResultWindow_window = "Rectangle search results";
  namedWindow( myRectangleResultWindow_window, CV_WINDOW_AUTOSIZE );
  imshow( myRectangleResultWindow_window, src_orig );

  waitKey();
  return 0;

  Mat src_reference = src_reference_orig;//calculateHistExpandContract(src_reference_orig);
  Mat src_reference_gray;
  cvtColor( src_reference, src_reference_gray, CV_BGR2GRAY );

  OrbFeatureDetector detector_orb(500, ORB::CommonParams());
  FastFeatureDetector detector(10);

  std::vector<KeyPoint> keypoints_src, keypoints_ref;

  detector.detect( src_reference_orig, keypoints_ref );
  detector.detect( src, keypoints_src );


  //-- Step 2: Calculate descriptors (feature vectors)
  SurfDescriptorExtractor extractor_surf;
  OrbDescriptorExtractor extractor(ORB::CommonParams(1.44,2,2,0));


  Mat descriptors_ref, descriptors_src;

  extractor.compute( src_reference_orig, keypoints_ref, descriptors_ref );
  extractor.compute( src, keypoints_src, descriptors_src );


  //-- Draw only "good" matches (i.e. whose distance is less than 3*min_dist )
  std::vector< DMatch > good_matches;


  Mat corners_ref;
  goodFeaturesToTrack(src_reference_gray,corners_ref,100,0.08,9,noArray(),5,0,0.04);
  Subdiv2D voronioTesselator(Rect(0,0,src_reference_gray.cols,src_reference_gray.rows));
  voronioTesselator.insert(corners_ref);
  std::vector<Vec4f> edgeList;
  voronioTesselator.getEdgeList(edgeList);


  for (vector<Vec4f>::iterator it = edgeList.begin(); it!=edgeList.end(); ++it) {
	  Point start = Point((*it)[0], (*it)[1]);
	  Point end = Point((*it)[2],(*it)[3]);
      line(src_reference_orig,start,end,Scalar(255,0,100,100),1);
  }

  for (int i=0;i < corners_ref.rows;i++) {
	  Point2f p = corners_ref.at<Point2f>(i,0);
       circle(src_reference_orig, p, 5, Scalar(255,0,100,100),2);
   }

  drawKeypoints(src_reference_orig,keypoints_ref, src_reference_orig, Scalar(255,255,0,0),DrawMatchesFlags::DRAW_OVER_OUTIMG||DrawMatchesFlags::DRAW_RICH_KEYPOINTS);
  /* Create Window and Trackbar */
  namedWindow( myReferenceImage_window, CV_WINDOW_AUTOSIZE );
  imshow( myReferenceImage_window, src_reference_orig );


  Mat keypoints_mat(keypoints_src.size(),2,CV_32F);
  for (unsigned int k=0;k < keypoints_src.size();k++) {
	  keypoints_mat.at<float>(k,0) = keypoints_src[k].pt.x;
	  keypoints_mat.at<float>(k,1) = keypoints_src[k].pt.y;
  }

  cv::flann::Index flann_keypoints(keypoints_mat,cv::flann::LinearIndexParams());

  if (false) {
	  for (unsigned int k=0;k<keypoints_src.size();k++) {
		  vector<float> searchkeypoint;
		  vector<float > distances;
		  vector<int> indices;
		  searchkeypoint.push_back(keypoints_src[k].pt.x);
		  searchkeypoint.push_back(keypoints_src[k].pt.y);
		  int found = flann_keypoints.radiusSearch(searchkeypoint,indices,distances,3000.0, 100);

		  for (int j= 0;j<found;j++) {
			  unsigned int index = indices[j];
			  if (index < keypoints_src.size()) {
				  float radius = distances[j];

				  if (10 < radius && radius < 160) {
					  KeyPoint keypoint = keypoints_src[index];
					  line(src_orig,keypoints_src[k].pt,keypoint.pt,Scalar(255,145,145,255),2);
				  }
			  } else {
				  printf("stop");
			  }
		  }
	  }
  }

  cvtColor( src, src_gray, CV_BGR2GRAY );
  Mat dst_hist;

  equalizeHist( src_gray, dst_hist );

  //namedWindow( myHistEqu_window, CV_WINDOW_AUTOSIZE );
  //imshow( myHistEqu_window, dst_hist );


  /// Set some parameters
  int blockSize = 9; int apertureSize = 9;

  /// My Harris matrix -- Using cornerEigenValsAndVecs
  myHarris_dst = Mat::zeros( src_gray.size(), CV_32FC(6) );
  Mc = Mat::zeros( src_gray.size(), CV_32FC1 );

  cornerEigenValsAndVecs( src_gray, myHarris_dst, blockSize, apertureSize, BORDER_DEFAULT );

  /* calculate Mc */
  for( int j = 0; j < src_gray.rows; j++ )
     { for( int i = 0; i < src_gray.cols; i++ )
          {
            float lambda_1 = myHarris_dst.at<float>( j, i, 0 );
            float lambda_2 = myHarris_dst.at<float>( j, i, 1 );
            Mc.at<float>(j,i) = lambda_1*lambda_2 - 0.04*pow( ( lambda_1 + lambda_2 ), 2 );
          }
     }

  minMaxLoc( Mc, &myHarris_minVal, &myHarris_maxVal, 0, 0, Mat() );

  /* Create Window and Trackbar */
  //namedWindow( myHarris_window, CV_WINDOW_AUTOSIZE );
  //createTrackbar( " Quality Level:", myHarris_window, &myHarris_qualityLevel, max_qualityLevel, myHarris_function );
  //myHarris_function( 0, 0 );

  /// My Shi-Tomasi -- Using cornerMinEigenVal
  myShiTomasi_dst = Mat::zeros( src_gray.size(), CV_32FC1 );
  cornerMinEigenVal( src_gray, myShiTomasi_dst, blockSize, apertureSize, BORDER_DEFAULT );

  minMaxLoc( myShiTomasi_dst, &myShiTomasi_minVal, &myShiTomasi_maxVal, 0, 0, Mat() );

  /* Create Window and Trackbar */
  namedWindow( myShiTomasi_window, CV_WINDOW_AUTOSIZE );
  createTrackbar( " Quality Level:", myShiTomasi_window, &myShiTomasi_qualityLevel, max_qualityLevel, myShiTomasi_function );
  myShiTomasi_function( 0, 0 );

  equalizeHist( src_gray, dst_hist );

  Mat corners;
  goodFeaturesToTrack(src_gray,corners,100,0.08,9,noArray(),5,0,0.04);

  for (int i=0;i < corners.rows;i++) {
	  Point2f p = corners.at<Point2f>(i,0);
       circle(src_orig, p, 5, Scalar(255,0,100,100),2);
   }

  Rect boundingBox(0,0,src_orig.cols,src_orig.rows);
  Subdiv2D voronioTesselatorSrc(boundingBox);
  voronioTesselatorSrc.insert(corners);
  std::vector<Vec4f> edgeSrcList;
  voronioTesselatorSrc.getEdgeList(edgeSrcList);


  for (vector<Vec4f>::iterator it = edgeSrcList.begin(); it!=edgeSrcList.end(); ++it) {
	  Point start = Point((*it)[0], (*it)[1]);
	  Point end = Point((*it)[2],(*it)[3]);
      line(src_orig,start,end,Scalar(128,0,100,100),1);
  }

  std::vector<Vec6f> triangleSrcList;
  voronioTesselatorSrc.getTriangleList(triangleSrcList);

  for (vector<Vec6f>::iterator it = triangleSrcList.begin(); it!=triangleSrcList.end(); ++it) {

	  Point2f a = Point2f((*it)[0], (*it)[1]);
	  Point2f b = Point2f((*it)[2],(*it)[3]);
	  Point2f c = Point2f((*it)[4],(*it)[5]);
	  //Ok first prune rectangles from the seed trianbles
	  if (boundingBox.contains(a) && boundingBox.contains(b) && boundingBox.contains(c)) {
		  Scalar color = Scalar(255,200,100,100);
		  int width = 1;
		  //Create the vectors
		  Point2f v1 = b - a;
		  Point2f v2 = c - a;
		  Point2f v3 = c - b;
		  Point2f d;

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
		  	  Point2f e;
		  	  voronioTesselatorSrc.findNearest(d,&e);
		  	  color = Scalar(255,50,200,100);

		  	  width = 3;
		  	  if (magnitude_sqr(d-e) < 9.0) {
			  	  color = Scalar(55,50,200,100);
		  		  //A rectangle!!
		  		 width =5;
		  	  }
		  }
		  line(src_orig,a,b,color,width);
		  line(src_orig,a,c,color,width);
		  line(src_orig,b,c,color,width);
	  }
  }

  /* Create Window and Trackbar */
  //drawKeypoints(src_orig,keypoints_src, src_orig, Scalar(255,255,0),DrawMatchesFlags::DRAW_OVER_OUTIMG||DrawMatchesFlags::DRAW_RICH_KEYPOINTS);

  namedWindow( myInputImage_window, CV_WINDOW_AUTOSIZE );
  imshow( myInputImage_window, src_orig );

  waitKey(0);
  return(0);
}

/**
 * @function myShiTomasi_function
 */
void myShiTomasi_function( int, void* param)
{
	Mat* src = (Mat*)param;
  myShiTomasi_copy = src->clone();

  if( myShiTomasi_qualityLevel < 1 ) { myShiTomasi_qualityLevel = 1; }

  for( int j = 0; j < myShiTomasi_copy.rows; j++ )
     { for( int i = 0; i < myShiTomasi_copy.cols; i++ )
          {
            if( myShiTomasi_dst.at<float>(j,i) > myShiTomasi_minVal + ( myShiTomasi_maxVal - myShiTomasi_minVal )*myShiTomasi_qualityLevel/max_qualityLevel )
              { circle( myShiTomasi_copy, Point(i,j), 4, Scalar( rng.uniform(0,255), rng.uniform(0,255), rng.uniform(0,255) ), -1, 8, 0 ); }
          }
     }
  imshow( myShiTomasi_window, myShiTomasi_copy );
}

/**
 * @function myHarris_function
 */
void myHarris_function( int, void* param)
{
	Mat* src = (Mat*)param;
	myHarris_copy = src->clone();

  if( myHarris_qualityLevel < 1 ) { myHarris_qualityLevel = 1; }

  for( int j = 0; j < myHarris_copy.rows; j++ )
     { for( int i = 0; i < myHarris_copy.cols; i++ )
          {
            if( Mc.at<float>(j,i) > myHarris_minVal + ( myHarris_maxVal - myHarris_minVal )*myHarris_qualityLevel/max_qualityLevel )
              { circle( myHarris_copy, Point(i,j), 4, Scalar( rng.uniform(0,255), rng.uniform(0,255), rng.uniform(0,255) ), -1, 8, 0 ); }
          }
     }
  imshow( myHarris_window, myHarris_copy );
}






