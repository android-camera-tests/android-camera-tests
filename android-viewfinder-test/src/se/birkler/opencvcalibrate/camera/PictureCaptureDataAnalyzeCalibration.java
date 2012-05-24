package se.birkler.opencvcalibrate.camera;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import se.birkler.opencvcalibrate.opencvutil.CaptureDataAction;
import se.birkler.opencvcalibrate.opencvutil.MatBitmapHolder;
import se.birkler.opencvcalibrate.util.XLog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Log;

public class PictureCaptureDataAnalyzeCalibration extends PictureCaptureData implements CaptureDataAction {
	
	private CalibrationEntries mCalibrationEntries;
	PictureCaptureDataAnalyzeCalibration(CalibrationEntries calibrationEntries) {
		mCalibrationEntries = calibrationEntries;
	}
	
	boolean decodeJPEGAndAnalyze(Context context, CalibrationEntries calibrationEntries) {
		boolean result = false;
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inPreferQualityOverSpeed = true;
		BitmapRegionDecoder brd;
		byte[] data = mData;
		try {
			brd = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
			Rect rect;
			rect = new Rect(0,0,brd.getWidth(),brd.getHeight());
			Bitmap bitmap = brd.decodeRegion(rect, opt);
			MatBitmapHolder mRgbaMatHolder = new MatBitmapHolder(bitmap);    	    
			Mat rgbaMat = mRgbaMatHolder.pin();
			Mat grayData = new Mat();
			Imgproc.cvtColor(rgbaMat, grayData, Imgproc.COLOR_RGB2GRAY, 1);
			Mat centersCalibCircles = new Mat();
			
	        Size patternSize = CalibrationEntries.Pattern_ASYMMETRIC_CIRCLES_GRID_SIZE;
	        Size imageSize = new Size(grayData.cols(),grayData.rows());
	        boolean patternWasFound = Calib3d.findCirclesGridDefault(grayData, patternSize, centersCalibCircles,Calib3d.CALIB_CB_ASYMMETRIC_GRID);
			
			if (patternWasFound && this.orientation != null) {
				int addedIdx = calibrationEntries.addEntry(this.orientation, centersCalibCircles);
				if (addedIdx >= 0) {
					Log.d("CALIB", String.format("PictureCapture: Added calibration entry at %d tot: %d", addedIdx,calibrationEntries.getNumEntries()));
					if (calibrationEntries.getNewlyAdded() > 5) {
						List<Mat> imagePoints= calibrationEntries.getPoints();
						List<Mat> objectPoints= calibrationEntries.getObjectPointsAsymmentricList(imagePoints.size());
						if (CalibrationEntries.isEnoughCalibrationPoints(imagePoints.size())) {
							calibrationEntries.resetNewlyAdded();
							CameraCalibrationData cameraCalibrationData = new CameraCalibrationData();
							List<Mat> rvecs = new Vector<Mat>(imagePoints.size());
							List<Mat> tvecs = new Vector<Mat>(imagePoints.size());
							int flags = 0;
							flags |= Calib3d.CALIB_FIX_K4 | Calib3d.CALIB_FIX_K5; 
							Log.d("CALIB", String.format("PictureCapture: Calling Calib3d.calibrateCamera"));
							Mat K = new Mat();
							Mat kdist = new Mat();
							double rms = Calib3d.calibrateCamera(objectPoints, imagePoints, imageSize, K, kdist, rvecs, tvecs, flags);
							double[] Karray = new double[9];
							double[] distcoeffs_array = new double[5];
							K.get(0, 0, Karray);
							kdist.get(0, 0, distcoeffs_array);
							cameraCalibrationData.setData(grayData.cols(),grayData.rows(),Karray,distcoeffs_array,rms);
							Log.d("CALIB", String.format("PictureCapture: Calibration data: %s", cameraCalibrationData.formatCalibrationDataString()));
							calibrationEntries.setCalibrationData(cameraCalibrationData);
							result = true;
						}
					}
				}
			}

			//mRightBitmap = brd.decodeRegion(rect, opt);
			rect = new Rect(brd.getWidth() - brd.getWidth()/3,0,brd.getWidth(),brd.getHeight());
			//mLeftBitmap = brd.decodeRegion(rect, opt);
			
			mRgbaMatHolder.unpin(rgbaMat);
			
		} catch (IOException e) {
			XLog.e("Region decoder doesn't want to cooperate",e);
		} 
		
		return result;
	}

	@Override
	public boolean execute(Context context) {
		return decodeJPEGAndAnalyze(context,mCalibrationEntries);
	}
}


