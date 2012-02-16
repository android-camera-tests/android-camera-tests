package com.sizetool.accelcalibration.camera;

import java.util.List;

import com.sizetool.accelcalibration.util.XLog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;

public class PreviewViewRoot extends RelativeLayout {

	protected SurfaceView mSurfaceView;
	protected SurfaceHolder mHolder;
	protected SurfaceView mResultSurfaceView;
	protected SurfaceHolder mResultHolder;
    protected List<Size> mSupportedPreviewSizes;
    protected Size mPreviewSize;


	public PreviewViewRoot(Context context) {
		super(context);
	}

	public PreviewViewRoot(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PreviewViewRoot(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

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
	protected void onLayout(boolean changed, int l, int t,
			int r, int b) {
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


	Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
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
    	if (this.isInEditMode()) {
	    	super.onDraw(canvas);
	    	Paint p = new Paint();
	    	p.setColor(Color.CYAN);
	    	p.setAlpha(255);
	    	p.setStrokeWidth((float) 3.0);
	    	canvas.drawRect(0, 0,getWidth(),getHeight(), p);
    	}
    }

}