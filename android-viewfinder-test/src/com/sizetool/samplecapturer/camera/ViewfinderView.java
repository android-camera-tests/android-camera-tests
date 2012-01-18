/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sizetool.samplecapturer.camera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {
	private static final long ANIMATION_DELAY = 50L;

	private final Paint paint;
	private Rect mFramingRectCurrent;
	private Rect mFramingRectTarget;
	private long mLastTimerTick;
	private double anim_speed_px_per_ms = 0.02;

	private Matrix mResultPointConversionMatrix;
	
	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		paint = new Paint();
		Resources resources = getResources();
	}
	
	public void setFramingRect(Rect rect) {
		mFramingRectTarget = new Rect(rect);
		invalidate();
	}
	
	public void setResultPointConversionMatrix(Matrix matrix) {
		mResultPointConversionMatrix = matrix;
		invalidate();
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		long current_tick = System.currentTimeMillis();
		if (mFramingRectCurrent == null) {
			int w = canvas.getWidth()/2;
			int h = canvas.getHeight()/2;
			int s = Math.min(w,h);
			mFramingRectTarget = new Rect(w - s,h-s,w+s,h+s);
			s /=8;
			mFramingRectCurrent = new Rect(w - s,h-s,w+s,h+s);
			mLastTimerTick = current_tick;
		}
		int delta_ms = (int) (current_tick - mLastTimerTick);
		Rect frame = mFramingRectCurrent;
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		// Draw the exterior (i.e. outside the framing rect) darkened
		

		if (mResultPointConversionMatrix != null) {
			int center_x =  frame.centerX();
			int center_y = frame.centerY();
		}
	}
	
	private void drawResultPoint(Canvas c,int center_x, int center_y, Point point,float radius,Paint paint) {
		float pt[] = new float[2];
		pt[0] = point.x;
		pt[1] = point.y;
		mResultPointConversionMatrix.mapPoints(pt);
		c.drawCircle(pt[0],pt[1], radius, paint);
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode
	 *            An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode) {
		invalidate();
	}

	public void drawViewfinder() {
		// TODO Auto-generated method stub
		
	}
}
