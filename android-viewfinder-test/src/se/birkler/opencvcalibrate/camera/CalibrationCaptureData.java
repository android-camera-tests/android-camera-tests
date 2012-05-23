package se.birkler.opencvcalibrate.camera;

import android.content.Context;



public abstract class CalibrationCaptureData extends CaptureData {
	public float K[];  // float[9]: [fx 0 cx ; 0 fy cy; 0 0 1]
	public float distCoeffs[];  // float[5] : [k1 k2 p1 p2 k3]
	
	abstract boolean execute(Context context);
}
