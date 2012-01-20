#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <vector>

using namespace std;
using namespace cv;

extern "C" {
JNIEXPORT void JNICALL Java_com_sizetool_samplecapturer_camera_SampleCatcherActivity_FindFeatures(JNIEnv* env, jclass thizclass, jlong addrGray, jlong addrRgba)
{
    Mat* pMatGr=(Mat*)addrGray;
    Mat* pMatRgb=(Mat*)addrRgba;
    vector<KeyPoint> v;

    OrbFeatureDetector detector(50);

    detector.detect(*pMatGr, v);
    for( size_t i = 0; i < v.size(); i++ )
        circle(*pMatRgb, Point(v[i].pt.x, v[i].pt.y), 10, Scalar(255,0,0,255));
}

JNIEXPORT jlong JNICALL Java_com_sizetool_samplecapturer_camera_MatByteBufferWrapper_nativeCreateMat(JNIEnv* env,jclass c, jobject bytebuff, jint rows, jint cols, jint type)
{
	void* p = env->GetDirectBufferAddress(bytebuff);
	Mat* m = new Mat::Mat(rows,cols,type,p);
    return (jlong)m;
}

}
