package se.birkler.opencvcalibrate.camera;

import android.content.Context;



public abstract class CalibrationCaptureData extends CaptureData {
	abstract boolean execute(Context context);
}
