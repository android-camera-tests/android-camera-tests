package com.sizetool.samplecapturer.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

public class CroppedFrameView extends FrameLayout {
	final static int MIN_MEASURE_SIZE = 10;
	int mPreviewHeight = 0;
	int mPreviewWidth = 0;
	boolean mCrop = false;
	
	public CroppedFrameView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CroppedFrameView(Context context) {
		this(context, null, 0);
	}
	
	public CroppedFrameView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	public void setAspectRatio(boolean crop, int width, int height) {
		if (mCrop != crop || mPreviewWidth != width || mPreviewHeight != height) {
			mCrop = crop;
			mPreviewWidth = width;
			mPreviewHeight = height;
			requestLayout();
		}
	}

	protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec,heightMeasureSpec);
		//float aspect = 3/4;
		//int width = getMeasuredWidth();
		//int height= getMeasuredHeight();
		//if (height * aspect > width) {
			//We need adjust width
		//}
		//else {
			//We need adjust height
		//}
	}
	
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    	super.onLayout(changed,l,t,r,b);
        int frameWidth = getWidth();
        int frameHeight = getHeight();

        if (frameWidth > MIN_MEASURE_SIZE && frameHeight > MIN_MEASURE_SIZE && mPreviewWidth > MIN_MEASURE_SIZE && mPreviewHeight > MIN_MEASURE_SIZE)
        {
	        float mAspectRatio;
	        if (frameWidth > frameHeight) {
	        	//Landscape...
	        	mAspectRatio = (float) mPreviewWidth / (float)mPreviewHeight;
	        }
	        else {
	        	//Portrait, swap preview stuff since it now means something different.
	        	mAspectRatio = (float) mPreviewHeight / mPreviewWidth;
	        }
	        
	        float currentAspectRatio = (float) frameWidth / (float)frameHeight;
	        
	        
	        if (mCrop) {
		        if (currentAspectRatio > mAspectRatio) {
		        	//Current width is to wide, increase height
		        	frameHeight = (int) (frameWidth / mAspectRatio);
		        }
		        else {
		        	//Current height is to high, increase width
		        	frameWidth = (int) (frameHeight * mAspectRatio);
		        }
	        }
	        else {
	        	//Black border
		        if (currentAspectRatio > mAspectRatio) {
		        	//Current width is to wide, decrease it
		        	frameWidth = (int) (frameHeight * mAspectRatio);
		        }
		        else {
		        	//Current height is to high, decrease it
		        	frameHeight = (int) (frameWidth / mAspectRatio);
		        }
	        }
	
	        int cx = (l+r) / 2;
	        int cy = (t+b) / 2;
	        
	        int l2 = cx - frameWidth / 2;
	        int r2 = cx + frameWidth / 2;
	        int t2 = cy - frameHeight / 2;
	        int b2 = cy + frameHeight / 2;
	        
	        for (int i = 0; i < this.getChildCount(); i++) {
	        	View v = this.getChildAt(i);
	        	v.layout(l2,t2,r2,b2);
	        }
        }
        else {
        	//Assume children have been layed out by the super call above
        }
    }
}
