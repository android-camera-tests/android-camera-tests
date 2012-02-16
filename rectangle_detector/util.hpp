#ifndef __UTIL_OPENCV_HELPERS_HPP__
#define __UTIL_OPENCV_HELPERS_HPP__



using namespace cv;

typedef Vec<float, 8> Vec8f;


float magnitude_sqr(Point2f p) {
	return p.x*p.x + p.y*p.y;
}

/**
 * This is cross product of vectors [{p1-p0},0] and [{p2-p0},0]
 */
float signed_area_of_triangle(Point2f p0, Point2f p1, Point2f p2)
{
	return ((p1.x-p0.x)*(p2.y-p0.y) - (p2.x-p0.x)*(p1.y-p0.y)) / 2;
}


/**
 * This is cross product of vectors [{p1-p0},0] and [{p2-p0},0]
 */
float area_of_rectangle(Point2f p[4])
{
	return (abs(signed_area_of_triangle(p[0],p[1],p[2])) + abs(signed_area_of_triangle(p[3],p[1],p[2])));
}


class Histogram {
private:
	Mat histogram;
	unsigned int totalPixels;
	float mean;

	/// Establish the number of bins
public:
	const int histSize;

	Histogram() :
		mean(-1),
		histSize(255)
	{
	}

	Histogram(const Mat& referenceImgforHistogram) :
		mean(-1),
		histSize(255)
	{
		init(referenceImgforHistogram);
	}


	void init(const Mat& referenceImgforHistogram){
		 float range[] = { 0, 255 } ;
		 const float* histRange = { range };
		 bool uniform = true;
		 bool accumulate = false;
		 Mat img;

		 totalPixels = referenceImgforHistogram.cols * referenceImgforHistogram.rows;
		 ///to gray if color image
		 if (referenceImgforHistogram.channels() > 1) {
			 cvtColor( referenceImgforHistogram, img, CV_BGR2GRAY );
		 } else {
			 img = referenceImgforHistogram;
		 }
		 /// Compute the histogram:
		 calcHist( &img, 1, 0, Mat(), histogram, 1, &histSize, &histRange, uniform, accumulate );
	}

	const Mat& getHistogramMat(void) const
	{
		return histogram;
	}


	float getMean()
	{
		if (mean < 0) {
			float total = 0;
			float sum = 0;
			for (int r=0;r<histogram.rows;r++) {
				float value = histogram.at<float>(r);
				total += value;
				sum += value*r;
			}
			mean = (sum/total);
		}
		return mean;
	}

	int getPercentileBin(float percentile)
	{
		//TODO:Use integer math? (seems histogram is returned as float?)
		 float targetSumOfPixels = totalPixels * percentile;
		 float sum = 0;
		 unsigned int bin_idx = 0;
		 while (bin_idx < 255 && sum < targetSumOfPixels) {
			 int value = histogram.at<float>(bin_idx);
			 sum += value;
			 bin_idx++;
		 }
		 return bin_idx;
	}

	int getPercentileBinReverse(float percentile)
	{
		//TODO:Use integer math? (seems histogram is returned as float?)
		 float targetSumOfPixels = totalPixels * percentile;
		 float sum = 0;
		 int bin_idx = 255;
		 while (bin_idx >=0 && sum < targetSumOfPixels) {
			 int value = histogram.at<float>(bin_idx);
			 sum += value;
			 bin_idx--;
		 }
		 return bin_idx;
	}


};



/**
 * Stretches the contrast on the image by ignoring black up to certain
 *
 * Calculates the histogram from referenceImgforHistogram
 * then determines the levelB to reach the blackLevelPercentile and
 * levelW to reach the whiteLevelPercentile.
 *
 * The applyStretch is then contrast stretch such that all pixels with levelB or lower become black
 * and all pixels levelW or higher become white.
 *
 */
int stretchContrastFromHistogram(const Mat& referenceImgforHistogram, Mat& applyStretch, float blackLevelPercentile, float whiteLevelPercentile)
{
	 Histogram histogram;
	 histogram.init(referenceImgforHistogram);

	 float autocorrect_mean_value = histogram.getMean();
	 int autocorrect_max_value = histogram.getPercentileBinReverse(whiteLevelPercentile);
	 int autocorrect_min_value = histogram.getPercentileBin(blackLevelPercentile);
	 if (autocorrect_max_value < autocorrect_mean_value) autocorrect_max_value = autocorrect_mean_value;
	 if (autocorrect_min_value > autocorrect_mean_value) autocorrect_min_value = autocorrect_mean_value;

	 if (autocorrect_max_value < autocorrect_min_value + 5) return -1;

	 applyStretch = applyStretch - Scalar(autocorrect_min_value,autocorrect_min_value,autocorrect_min_value);
	 applyStretch = applyStretch * (255.0 / (autocorrect_max_value - autocorrect_min_value));

	 //printf("stretch range:%d-%d",autocorrect_min_value,autocorrect_max_value);
	 return 0;
}




#endif // #ifndef __UTIL_OPENCV_HELPERS_HPP__


