package se.birkler.opencvcalibrate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import se.birkler.opencvcalibrate.opencvutil.MatByteBufferWrapper;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

public class GuidanceImageView extends ImageView {
	
	// This constructor is used when the class is built from an XML resource.
	public GuidanceImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

}