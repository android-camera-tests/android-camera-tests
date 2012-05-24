package se.birkler.opencvcalibrate.camera;

import java.io.File;

import se.birkler.opencvcalibrate.opencvutil.CaptureDataAction;
import android.content.Context;


public class PictureCaptureDataWriteToDisk extends PictureCaptureData implements CaptureDataAction  {
	protected File mToFile;	
	
	PictureCaptureDataWriteToDisk(File toFile) {
		super();
		mToFile = toFile;
	}
	
	@Override
	public boolean execute(Context context) {
		writeFile(context,mToFile);
		return true;
	}
}
