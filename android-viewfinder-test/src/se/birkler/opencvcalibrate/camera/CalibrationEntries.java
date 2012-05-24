/**
 * Copyright 2012 Jorgen Birkler
 * jorgen@birkler.se
 *
 * Camera calibration and open cv demonstration applications
 *
 */


package se.birkler.opencvcalibrate.camera;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;


/**
 *
 * This class hold calibration data (points from findCircles and an absolute world rotation matrix)
 * 
 * Class tries to figure out if the entry to be added increases calibration confidence data set or not.
 * Only if the new calibration entry is "better" than other calibration entries does it store it in the array.
 * 
 * Once haveEnoughCalibrationData return true the points can be feed to camera calibration routines
 *
 * Outline:
 * - Create new entry and add data to list of calibration points
 * - Calculate relative angle between _all_ calibration entries
 * - Calculate L1 distance of each of the entries. 
 * - Sort entries by falling L1 distance
 * - Remove last entry if to many entries 
 * .
 * 
 */

public class CalibrationEntries {
	CameraCalibrationData mCameraCalibrationData = null;
	
	static final int Pattern_CHESSBOARD = 1;
	static final int Pattern_CIRCLES_GRID = 2;
	static final int Pattern_ASYMMETRIC_CIRCLES_GRID = 3;
	static final Size Pattern_ASYMMETRIC_CIRCLES_GRID_SIZE = new Size(4,11);


	final static int CALIBRATION_ENTRIES_MIN = 8;
	final static int CALIBRATION_ENTRIES = 10;
	final static int CANDIDATE_INDEX = 0;
	final static int CALIBRATION_ENTRIES_TOTAL = CALIBRATION_ENTRIES + 1;
	Vector<CalibrationEntry> entries = new Vector<CalibrationEntry>(CALIBRATION_ENTRIES_TOTAL + 1);
	Mat x_axis_unit_vector;
	private Mat mAsymmetricalCalibrationObjectPoints = null;
	private int mNewlyAdded;
	
	CalibrationEntries() {
		x_axis_unit_vector = Mat.zeros(3,1,CvType.CV_32F);
		x_axis_unit_vector.put(0, 0, 1.0);
	}
	
	int getNumEntries() {
		return entries.size();
	}
	
	int addEntry(float RotMatrixArr[], Mat points) {
		Mat rot_axis_vector = new Mat(3,1,CvType.CV_32F);
		Mat R = new Mat(3,3,CvType.CV_32F);
		R.put(0, 0,RotMatrixArr); 

		Core.gemm(R, x_axis_unit_vector, 1.0, Mat.zeros(3,3,CvType.CV_32F), 0.0, rot_axis_vector);
		
		CalibrationEntry entry = new CalibrationEntry(R,points,rot_axis_vector,CALIBRATION_ENTRIES_TOTAL);
		entries.add(entry);
		
		updateDistanceMatrixAndSort();
		
		int idx = entries.indexOf(entry);
		if (idx >= 0) {
			mNewlyAdded++;
		}
		if (entries.size() > CALIBRATION_ENTRIES) {
			CalibrationEntry entryRemoved = entries.remove(entries.size() - 1);
			if (entryRemoved == entry) return -1;
		}
		return idx;
	}
	
	public int getNewlyAdded() {
		return mNewlyAdded;
	}
	
	public void resetNewlyAdded() {
		mNewlyAdded = 0;
	}

	static double calculateRotationAngle(Mat Rfrom, Mat Rto) {
		Mat Rdelta = new Mat(3,3,CvType.CV_32F);
		Core.gemm(Rfrom, Rto, 1.0, Mat.zeros(3,3,CvType.CV_32F), 0.0, Rdelta,Core.GEMM_2_T);
		//Core.multiply(Rnow, Rref.inv(), Rdelta); <= don't use: broken
		Mat rvect = new Mat(1,3,CvType.CV_32F);
		Calib3d.Rodrigues(Rdelta, rvect);
		double angle = Core.norm(rvect);
		return angle;
	}
	
	
	void updateDistanceMatrixAndSort() {
		//Update candidate row
		Mat Vfrom;
		Mat Vto;
		Vfrom = entries.lastElement().mRotAxisProjection;
		for (int i=0; i < CALIBRATION_ENTRIES_TOTAL;i++) {
			Mat Vdiff = new Mat(3,1,CvType.CV_32F);
			if (i < entries.size()) {
				Vto = entries.get(i).mRotAxisProjection;
				Core.subtract(Vto,Vfrom, Vdiff);
				double length = Core.norm(Vdiff);
				entries.lastElement().distances[i] = length;
			}
			else {
				entries.lastElement().distances[i] = 0;
			}
		}
		//Update invalidated column
		Vto = entries.lastElement().mRotAxisProjection;
		for (int i=0; i < entries.size();i++) {
			Mat Vdiff = new Mat(3,1,CvType.CV_32F);
			Vfrom = entries.get(i).mRotAxisProjection;
			Core.subtract(Vto,Vfrom, Vdiff);
			double length = Core.norm(Vdiff);
			entries.get(i).distances[entries.size() - 1] = length;
		}
		//Recalculate distances
		for (CalibrationEntry entry : entries) {
			entry.updateDistanceToOthers();
		}
		
		//Sort by distances
		Collections.sort(entries,entries.lastElement());
		
		//Weakest calibration point should now be last in entries;
	}
	
	static boolean isEnoughCalibrationPoints(int n) {
		return n >= CALIBRATION_ENTRIES_MIN;
	}



	public List<Mat> getPoints() {
		List<Mat> list = new Vector<Mat>(entries.size());
		for (CalibrationEntry entry : entries) {
			list.add(entry.centersCalibCircles);
		}
		return list;
	}
	
	
	
	public List<Mat>  getObjectPointsAsymmentricList(int nEntries) {
		if (mAsymmetricalCalibrationObjectPoints == null) {
			mAsymmetricalCalibrationObjectPoints = calculateCalibrationObjectPoints(Pattern_ASYMMETRIC_CIRCLES_GRID_SIZE,1.0f,Pattern_ASYMMETRIC_CIRCLES_GRID);
			
		}
		List<Mat> list = new Vector<Mat>(entries.size());
		for (int i=0;i<nEntries;i++) {
			list.add(mAsymmetricalCalibrationObjectPoints);
		}
		return list;
	}

	static private Mat calculateCalibrationObjectPoints(Size boardSize, float squareSize,  int patternType )
    {
		Mat corners = new Mat((int) (boardSize.height * boardSize.width),1,CvType.CV_32FC3);
		float data[] = new float[3];
		switch(patternType)
		{
			case Pattern_CHESSBOARD:
			case Pattern_CIRCLES_GRID:
				{
					int k=0;
					for( int i = 0; i < boardSize.height; ++i ) {
						for( int j = 0; j < boardSize.width; ++j ) {
							data[0] = (float)( j*squareSize );
							data[1] = (float)( i*squareSize );
							data[2] = 0.0f;
							corners.put(k,0, data);
							k++;
						}
					}
				}
				break;
			

			case Pattern_ASYMMETRIC_CIRCLES_GRID:
			{
				int k=0;
				for( int i = 0; i < boardSize.height; i++ ) {
					for( int j = 0; j < boardSize.width; j++ ) {
						data[0] = (float)( (2*j + i % 2)*squareSize );
						data[1] = (float)( i*squareSize );
						data[2] = 0.0f;
						corners.put(k,0, data);
						k++;
					}
				}
			}
			break;
		}
		return corners;
	}

	
	public void  setCalibrationData(CameraCalibrationData data) {
		mCameraCalibrationData = data;
	}
	public CameraCalibrationData getCalibrationData() {
		return mCameraCalibrationData;
	}

	public byte[] getBestJpegData() {
		return null;
	}

	 
	
	
}

class CalibrationEntry implements Comparator<CalibrationEntry> {
	final static double MIN_DISTANCE = 0.2;
	//Mat of 44x1xPoint2f of 4x11 calibration points from asymmetrical circle pattern
	Mat centersCalibCircles;
	//Absolute rotation matrix in world coordinated. With world Mat.eye being due north, level and parallel with ground.
	Mat absoluteRotation; 
	Mat mRotAxisProjection;
	
	double[] distances;
	
	CalibrationEntry(Mat R, Mat points,Mat rotAxisProjection, int max_entries) {
		centersCalibCircles = new Mat(points.rows(),1,points.type());
		points.copyTo(centersCalibCircles);
		absoluteRotation = R;
		mRotAxisProjection = rotAxisProjection;
		distances = new double[max_entries];
	}
	

	double calculateDistance(double[] arr) {
		double sum2 = 0.0;
		for (int i=0; i < arr.length;i++) {
			sum2 += arr[i] * arr[i];  
		}
		return Math.sqrt(sum2);
	}

	
	void updateDistanceToOthers() {
		distanceToOthers = calculateDistance(distances);
	}
	
	double distanceToOthers;
	
    // Comparator interface requires defining compare method.
    public int compare(CalibrationEntry a, CalibrationEntry b) {
    	double distA = a.distanceToOthers;
    	double distB = b.distanceToOthers;
    	
        //sort in reverse distance order (least last)
    	if (Math.abs(distB - distA) < MIN_DISTANCE) {
    		return 0;
    	}
    	else if (distA < distB) {
            return -1;
        } else {
        	return 1;
        }
    }
}
