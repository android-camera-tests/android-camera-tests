package se.birkler.opencvcalibrate.camera;

public class CameraCalibrationData {
	public double K[]; //[9]
	public double distcoeffs[]; //[5]
	public double rms;
	public int width; 
	public int height;
	public double hfov;
	public double vfov;
	
	String formatCalibrationDataString() {
		double fx = K[0];
		double fy = K[4];
		double px = K[2];
		double py = K[5];
		return String.format("rms=%.3f fx=%.1f fy=%.1f px=%.1f py = %.1f",rms, fx,fy,px,py);
	}

	public void setData(int w, int h, double K[], double[] distcoeffs,double rms) 
	{
		this.K = K;
		this.distcoeffs = distcoeffs;
		if (K.length != 9) throw new IllegalArgumentException("K matrix should be 3x3");
		if (distcoeffs.length != 5) throw new IllegalArgumentException("distcoeffs matrix should be 5x1");
		double fx = K[0];
		double fy = K[4];
		width = w;
		height = h;
		hfov = 2 * Math.atan(w / (2*fx)) * 180.0 / Math.PI; 
		vfov = 2 * Math.atan(h / (2*fy)) * 180.0 / Math.PI; 
	}
}
