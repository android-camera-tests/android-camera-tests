#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <vector>
#include <android/bitmap.h>
#include <android/log.h>

using namespace std;
using namespace cv;

extern "C" {
JNIEXPORT void JNICALL Java_se_birkler_samplecapturer_camera_SampleCatcherActivity_findFeatures(JNIEnv* env, jclass thizclass, jlong addrGray, jlong addrRgba)
{
    Mat* pMatGr=(Mat*)addrGray;
    Mat* pMatRgb=(Mat*)addrRgba;
    vector<KeyPoint> v;

    OrbFeatureDetector detector(50);

    detector.detect(*pMatGr, v);
    for( size_t i = 0; i < v.size(); i++ ) {
        circle(*pMatRgb, Point(v[i].pt.x, v[i].pt.y), 10, Scalar(255,0,0,255));
    }
}

#include "../../rectangle_detector/util.hpp"
#include "../../rectangle_detector/rectangle_detector.h"

float configBlackPercentileForRoom = 0.65f;
float configWhitePercentileForWhitePaperInRoom = 1.0f/4000.0f; //PAper ~20x20 pixels in VGA=>400/400000 = 1/4000
int configMaxConerPaperDetector = 100;


JNIEXPORT jint JNICALL Java_se_birkler_samplecapturer_camera_SampleCatcherActivity_findRectangles(JNIEnv* env, jclass thizclass, jlong addrGray,jfloatArray floatArray, jlong addrRgba)
{
    Mat* pMatGr=(Mat*)addrGray;
    Mat* pMatRgb=(Mat*)addrRgba;
    float* floatArrayElements = env->GetFloatArrayElements(floatArray,JNI_FALSE);
    if (floatArrayElements == 0) return -1;
    int no_rects=get_no_rects_from_size(env->GetArrayLength(floatArray));
	int n = detect_rectangles1(*pMatGr,floatArrayElements,no_rects,pMatRgb);
	env->ReleaseFloatArrayElements(floatArray,floatArrayElements,0);
	floatArray= 0;
    return no_rects;
}



JNIEXPORT jlong JNICALL Java_se_birkler_samplecapturer_opencvutil_ExtraUtil_nativeCreateMatFromBytebuffer(JNIEnv* env,jclass c, jobject bytebuff, jint rows, jint cols, jint type)
{
	void* p = env->GetDirectBufferAddress(bytebuff);
	Mat* m = new Mat::Mat(rows,cols,type,p);
    return (jlong)m;
}


JNIEXPORT jlong JNICALL Java_se_birkler_samplecapturer_opencvutil_MatBitmapHolder_nativeCreateMatFromBitmapAndLock(JNIEnv* env,jclass c, jobject bitmap)
{
	AndroidBitmapInfo bitmapInfo;
	if (ANDROID_BITMAP_RESUT_SUCCESS != AndroidBitmap_getInfo(env,bitmap, &bitmapInfo)) {
		return 0;
	}
	//__android_log_print(ANDROID_LOG_VERBOSE,"extrautil_jni","bitmapinfo [%dx%d]*%d",bitmapInfo.width,bitmapInfo.height,bitmapInfo.format);

	int matType;
	switch (bitmapInfo.format) {
		case ANDROID_BITMAP_FORMAT_RGBA_8888:
			matType = CV_8UC4;
			break;
		case ANDROID_BITMAP_FORMAT_A_8:
			matType = CV_8UC1;
			break;
		case ANDROID_BITMAP_FORMAT_NONE:
		case ANDROID_BITMAP_FORMAT_RGB_565:
		case ANDROID_BITMAP_FORMAT_RGBA_4444:
		default:
			return 0;
	}

	void* lockedData = 0;
	if (ANDROID_BITMAP_RESUT_SUCCESS != AndroidBitmap_lockPixels(env, bitmap, &lockedData)) {
		return 0;
	}
	//__android_log_print(ANDROID_LOG_VERBOSE,"extrautil_jni","bitmap locked data %p",lockedData);

	Mat* m = new Mat::Mat(bitmapInfo.height,bitmapInfo.width,matType,lockedData);
	//__android_log_print(ANDROID_LOG_VERBOSE,"extrautil_jni","Mat %p",m);
    return (jlong)m;
}

JNIEXPORT jint JNICALL Java_se_birkler_samplecapturer_opencvutil_MatBitmapHolder_nativeFreeMatAndUnlockBitmap(JNIEnv* env,jclass c, jlong matHandle, jobject bitmap)
{
	//TODO check ref count of Mat
	if (ANDROID_BITMAP_RESUT_SUCCESS != AndroidBitmap_unlockPixels(env, bitmap)) {
		return 0;
	}
	//__android_log_print(ANDROID_LOG_VERBOSE,"extrautil_jni","bitmap unlocked");
	return 0;
}


#if 0
class Mat::BitmapMap : public Mat {
	private jobject bitmapGlobalRef;
	private int refcount;
	public BitmapMap() {

	}
}


class NumpyAllocator : public MatAllocator
{
public:
    NumpyAllocator() {}
    ~NumpyAllocator() {}

    void allocate(int dims, const int* sizes, int type, int*& refcount,
                  uchar*& datastart, uchar*& data, size_t* step)
    {
        int depth = CV_MAT_DEPTH(type);
        int cn = CV_MAT_CN(type);
        const int f = (int)(sizeof(size_t)/8);
        int typenum = depth == CV_8U ? NPY_UBYTE : depth == CV_8S ? NPY_BYTE :
                      depth == CV_16U ? NPY_USHORT : depth == CV_16S ? NPY_SHORT :
                      depth == CV_32S ? NPY_INT : depth == CV_32F ? NPY_FLOAT :
                      depth == CV_64F ? NPY_DOUBLE : f*NPY_ULONGLONG + (f^1)*NPY_UINT;
        int i;
        npy_intp _sizes[CV_MAX_DIM+1];
        for( i = 0; i < dims; i++ )
            _sizes[i] = sizes[i];
        if( cn > 1 )
        {
            /*if( _sizes[dims-1] == 1 )
                _sizes[dims-1] = cn;
            else*/
                _sizes[dims++] = cn;
        }
        PyObject* o = PyArray_SimpleNew(dims, _sizes, typenum);
        if(!o)
            CV_Error_(CV_StsError, ("The numpy array of typenum=%d, ndims=%d can not be created", typenum, dims));
        refcount = refcountFromPyObject(o);
        npy_intp* _strides = PyArray_STRIDES(o);
        for( i = 0; i < dims - (cn > 1); i++ )
            step[i] = (size_t)_strides[i];
        datastart = data = (uchar*)PyArray_DATA(o);
    }

    void deallocate(int* refcount, uchar* datastart, uchar* data)
    {
        if( !refcount )
            return;
        PyObject* o = pyObjectFromRefcount(refcount);
        Py_INCREF(o);
        Py_DECREF(o);
    }
};


#endif


}
