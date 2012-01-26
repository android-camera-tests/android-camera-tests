#ifndef __UTIL_DEBUG_OPENCV_HELPERS_HPP__
#define __UTIL_DEBUG_OPENCV_HELPERS_HPP__

#include "util.hpp"

using namespace cv;


void plotHistogramToNewWindow(const Histogram& histogram,const char* histogramName)
{
	const int hist_w = 400;
	const int hist_h = 400;
	  Mat histImage( hist_w, hist_h, CV_8UC3, Scalar( 0,0,0) );
	  Mat r_hist, g_hist, b_hist, w_hist;

	  r_hist = histogram.getHistogramMat().clone();
	  g_hist = histogram.getHistogramMat().clone();
	  b_hist = histogram.getHistogramMat().clone();
	  w_hist = histogram.getHistogramMat().clone();

	  /// Normalize the result to [ 0, histImage.rows ]
	  normalize(r_hist, r_hist, 0, histImage.rows, NORM_MINMAX, -1, Mat() );
	  normalize(g_hist, g_hist, 0, histImage.rows, NORM_MINMAX, -1, Mat() );
	  normalize(b_hist, b_hist, 0, histImage.rows, NORM_MINMAX, -1, Mat() );
	  normalize(w_hist, w_hist, 0, histImage.rows, NORM_MINMAX, -1, Mat() );
	  int bin_w = cvRound( (double) hist_w/w_hist.rows );

	  /// Draw for each channel
	  for( int i = 1; i < w_hist.rows; i++ )
	    {
	      line( histImage, Point( bin_w*(i-1), hist_h - cvRound(r_hist.at<float>(i-1)) ) ,
			       Point( bin_w*(i), hist_h - cvRound(r_hist.at<float>(i)) ),
		               Scalar( 0, 0, 255), 2, 8, 0  );
	      line( histImage, Point( bin_w*(i-1), hist_h - cvRound(g_hist.at<float>(i-1)) ) ,
			       Point( bin_w*(i), hist_h - cvRound(g_hist.at<float>(i)) ),
		               Scalar( 0, 255, 0), 2, 8, 0  );
	      line( histImage, Point( bin_w*(i-1), hist_h - cvRound(b_hist.at<float>(i-1)) ) ,
			       Point( bin_w*(i), hist_h - cvRound(b_hist.at<float>(i)) ),
		               Scalar( 255, 0, 0), 2, 8, 0  );
	      line( histImage, Point( bin_w*(i-1), hist_h - cvRound(b_hist.at<float>(i-1)) ) ,
			       Point( bin_w*(i), hist_h - cvRound(b_hist.at<float>(i)) ),
		               Scalar( 200, 200, 200), 2, 8, 0  );
	    }

	 /// Display
	  std::string str = "Histogram:";
	  str += histogramName;
	  namedWindow(str, CV_WINDOW_AUTOSIZE );
	  imshow(str, histImage );

}

#endif //#ifndef __UTIL_DEBUG_OPENCV_HELPERS_HPP__
