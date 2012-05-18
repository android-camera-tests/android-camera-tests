package se.birkler.opencvcalibrate.camera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import se.birkler.opencvcalibrate.opencvutil.MatByteBufferWrapper;
import se.birkler.opencvcalibrate.util.XLog;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class OpenCVViewfinderView extends SurfaceView implements SurfaceHolder.Callback {
	
	public interface openCVProcessor {
		void processFrame(Canvas canvas, int width, int height, Mat yuvData, Mat grayData);
	}
	
    private static final String TAG = "Sample::SurfaceView";

    private Camera              mCamera;
    private SurfaceHolder       mHolder;
    private int                 mPreviewWidth;
    private int                 mPreviewHeight;

	protected ByteBuffer mPreviewCallbackBuffer;
	private openCVProcessor mProcessor;
	private MatByteBufferWrapper mYuv;
	private Mat mGraySubmat;
	
	// This constructor is used when the class is built from an XML resource.
	public OpenCVViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);
        mHolder = getHolder();
        mHolder.addCallback(this);
        //mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

    public int getFrameWidth() {
        return mPreviewWidth;
    }

    public int getFrameHeight() {
        return mPreviewHeight;
    }
    
    public void setProcessor(openCVProcessor processor)
    {
    	mProcessor = processor;
    }

    
    
    private Camera.Size pickVgaOrBestSize(List<Size> sizes, int w, int h) {
	    for (Camera.Size size : sizes) {
	    	if (size.width == 640 && size.height == 480) {
                return size; 
	    	}
	    }
	    // selecting optimal camera preview size
	    {
	    	Camera.Size candidateSize = null;
	        double minDiff = Double.MAX_VALUE;
	        for (Camera.Size size : sizes) {
	            if (Math.abs(size.height - h) < minDiff) {
	            	candidateSize = size;
	                minDiff = Math.abs(size.height - h);
	            }
	        }
	        return candidateSize;
	    }
    }


    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        Debug.startMethodTracing("opencvtrace_viewfinder");
        mCamera = Camera.open();
        try {
        	mCamera.setPreviewDisplay(holder); 
	        mCamera.setPreviewCallbackWithBuffer (new PreviewCallback() {
	            private int frameCount;
				private long prevTime;
	
				public void onPreviewFrame(byte[] data, Camera camera) {
	        		frameCount++;
	        		long now = System.currentTimeMillis();
	        		if (prevTime > 0) {
	        			long diff = now - prevTime;
	        			if (diff > 2000) {
	        				Log.d("OpenCVDemo:",String.format("Frames/s:%.2f data.size=%d",frameCount * 1000.0f / diff,data == null ? 0 : data.length));
	        				frameCount = 0;
	        				prevTime = now;
	        			}
	        		}
	        		else {
	        			prevTime = now;
	        		}
	        		if (mProcessor != null) {
	                    Canvas canvas = mHolder.lockCanvas();
	                    if (canvas != null) {
	                    	mProcessor.processFrame(canvas, getFrameWidth(),getFrameHeight(), mYuv,mGraySubmat);
	                        mHolder.unlockCanvasAndPost(canvas);
	                    }
	        		}
	                byte[] framedata = mPreviewCallbackBuffer.array();
	                mCamera.addCallbackBuffer(framedata);
	            }
	        });
	    } catch (IOException e) {
	    	XLog.e("Failing to setup camera params in surface created",e);
	    }
    }
    
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        Debug.stopMethodTracing();
        if (mCamera != null) {
            synchronized (this) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
        }
    }

    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        Log.i(TAG, "surfaceCreated");
        if (mCamera != null) {
            
        	try { 
	            Camera.Parameters params = mCamera.getParameters();
	        	List<Camera.Size> sizes = params.getSupportedPreviewSizes();
	            mPreviewWidth = width;
	            mPreviewHeight = height;
	
	            Camera.Size previewSize = pickVgaOrBestSize(sizes, width,  height);
	            mPreviewWidth = previewSize.width;
	            mPreviewHeight = previewSize.height;
	            params.setPreviewSize(getFrameWidth(), getFrameHeight());
	
	            sizes = params.getSupportedPictureSizes();
	            Camera.Size picSize = pickVgaOrBestSize(sizes, width,  height);
	            params.setPictureSize(picSize.width,picSize.height);
	            mCamera.setParameters(params);
	            try {
					mCamera.setPreviewDisplay(null);
				} catch (IOException e) {
					Log.e(TAG, "mCamera.setPreviewDisplay fails: " + e);
				}
	            params = mCamera.getParameters();
	            int bytesneeded = previewSize.height * previewSize.width * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8; 
	            mPreviewCallbackBuffer = ByteBuffer.allocateDirect(bytesneeded);
	            // initialize Mats before usage
	            mYuv = new MatByteBufferWrapper(mPreviewCallbackBuffer,getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
	            //mYuv = new Mat(getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
	            mGraySubmat = mYuv.submat(0, getFrameHeight(), 0, getFrameWidth());
	            mCamera.addCallbackBuffer(mPreviewCallbackBuffer.array());
	            mCamera.startPreview();
		    } catch (Exception e) {
		    	XLog.e("Failing to setup camera params in surface changed",e);
		    }
		}
    }




	public void takePicture(ShutterCallback shutter, PictureCallback jpeg) {
		if (mCamera != null) {
			mCamera.takePicture(shutter, null, jpeg);
			mCamera.startPreview();
		}
		
	}

	public Bitmap getLastFrame() {
		// TODO Auto-generated method stub
		return null;
	}
}