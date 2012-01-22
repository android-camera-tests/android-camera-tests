package com.sizetool.samplecapturer.camera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.sizetool.samplecapturer.camera.OpenCVViewfinderView.openCVProcessor;
import com.sizetool.samplecapturer.opencvutil.MatByteBufferWrapper;
import com.sizetool.samplecapturer.util.XLog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
public class PreviewView extends RelativeLayout implements SurfaceHolder.Callback {

    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;
	protected ByteBuffer mPreviewCallbackBuffer;
	private openCVProcessor mProcessor;
	private Mat mYuv;
	private Mat mGraySubmat;

    SurfaceView mResultSurfaceView;
    SurfaceHolder mResultHolder;
	
	
    public PreviewView(Context context, AttributeSet attr) {
        super(context,attr);

        mSurfaceView = new SurfaceView(context);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mSurfaceView.setLayoutParams(lp);
        addView(mSurfaceView);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mResultSurfaceView = new SurfaceView(context);
        lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mResultSurfaceView.setLayoutParams(lp);
        addView(mResultSurfaceView);
        mResultSurfaceView.setZOrderOnTop(true);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mResultHolder = mResultSurfaceView.getHolder();
        mResultHolder.setFormat(PixelFormat.TRANSPARENT);
        mResultHolder.addCallback(this);
        setWillNotDraw(false);
    }

    @Override
    public void  onFinishInflate () {
    	super.onFinishInflate();
    }
    
    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    public void switchCamera(Camera camera) {
       setCamera(camera);
       try {
           camera.setPreviewDisplay(mHolder);
       } catch (IOException exception) {
           XLog.e("IOException caused by setPreviewDisplay()", exception);
       }
       Camera.Parameters parameters = camera.getParameters();
       parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
       requestLayout();
       camera.setParameters(parameters);
    }

    //@Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(mPreviewSize.width, mPreviewSize.height);
        }
        else {
            setMeasuredDimension(640,480);
        }
        
        //final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        //final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        //setMeasuredDimension(width, height);
        //tMeasuredDimension(mPreviewSize.width, mPreviewSize.height);

    }

	@Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final int height = b - t;

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
        
        int previewWidth = width;
        int previewHeight = height;
        if (mPreviewSize != null) {
            previewWidth = mPreviewSize.width;
            previewHeight = mPreviewSize.height;
        }
        
        // Center the child SurfaceView within the parent.
        if (width * previewHeight > height * previewWidth) {
            final int scaledChildWidth = previewWidth * height / previewHeight;
            l = (width - scaledChildWidth) / 2;
            t = 0;
            r = (width + scaledChildWidth) / 2;
            b = height;
        } else {
            final int scaledChildHeight = previewHeight * width / previewWidth;
            l = 0;
            t = (height - scaledChildHeight) / 2;
            r = width;
            b = (height + scaledChildHeight) / 2;
        }
    	super.onLayout(changed,l,t,r,b);
    	mSurfaceView.layout(l, t,r, b);
    	mResultSurfaceView.layout(l, t,r, b);
    	Rect rect = new Rect();
    	mSurfaceView.getLocalVisibleRect(rect);
    	XLog.d("surfaceView:" + mSurfaceView.toString() + rect.toString());
    }
	
	

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
    	if (holder == mHolder) {
	    	initCamera();
	        try {
	            if (mCamera != null) {
	                mCamera.setPreviewDisplay(holder);
	            }
	        } catch (IOException exception) {
	            XLog.e("IOException caused by setPreviewDisplay()", exception);
	        }
    	}
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }
    
    public void setProcessor(openCVProcessor processor) {
    	mProcessor = processor;
    }
    
    private void initCamera() {
    	if (mCamera == null) {
    	    mCamera = Camera.open();
    	    setCamera(mCamera);
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
		                Canvas canvas = mResultHolder.lockCanvas();
		                if (canvas != null) {
		                	mProcessor.processFrame(canvas, mPreviewSize.width,mPreviewSize.height, mYuv,mGraySubmat);
		                	mResultHolder.unlockCanvasAndPost(canvas);
		                }
		    		}
		            byte[] framedata = mPreviewCallbackBuffer.array();
		            mCamera.addCallbackBuffer(framedata);
		        }
		    });
    	}
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;
        // Try to find vga
        for (Size size : sizes) {
            if (size.height == 480 && size.width == 640) {
            	return size;
            }
        }

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    
    @Override
    public void onDraw(Canvas canvas) {
    	super.onDraw(canvas);
    	Paint p = new Paint();
    	p.setColor(Color.CYAN);
    	p.setAlpha(128);
    	p.setStrokeWidth((float) 3.0);
    	canvas.drawRect(0, 0,getWidth(),getHeight(), p);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
    	if (holder == mHolder) {  
	        Camera.Parameters parameters = mCamera.getParameters();
	        if (mPreviewSize != null) {
	        	parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
	            mCamera.setParameters(parameters);
	            int bytesneeded = mPreviewSize.height * mPreviewSize.width * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8; 
	            mPreviewCallbackBuffer = ByteBuffer.allocateDirect(bytesneeded);
	            mCamera.addCallbackBuffer(mPreviewCallbackBuffer.array());
	            // initialize Mats before usage
	            mYuv = new MatByteBufferWrapper(mPreviewCallbackBuffer,mPreviewSize.height + mPreviewSize.height / 2, mPreviewSize.width, CvType.CV_8UC1);
	            //mYuv = new Mat(getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
	            mGraySubmat = mYuv.submat(0, mPreviewSize.height, 0, mPreviewSize.width);
	        }
	        mCamera.startPreview();
	        requestLayout();
    	}
    } 

	public void takePicture(ShutterCallback shuttercallback, PictureCallback piccallback) {
		if (mCamera != null) {
			mCamera.takePicture(shuttercallback, null, piccallback);
			mCamera.startPreview();
		}
	}

}