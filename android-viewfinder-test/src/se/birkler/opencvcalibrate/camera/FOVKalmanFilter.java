/**
 * Copyright 2012 jorgen birkler jorgen@birkler.se
 */

package se.birkler.opencvcalibrate.camera;

import org.ejml.data.DenseMatrix64F;
import org.ejml.simple.SimpleMatrix;



/**
 * This implements a kalman filter to determine the field of view
 * 
 * The code is using the simple unoptimized kalman filter code as from here:
 * http://code.google.com/p/efficient-java-matrix-library/wiki/KalmanFilterExamples
 * 
 * With the change that the 
 * H - state to measurement transform matrix is not constant
 * 
 * Where are estimating the x which is a vector of [d,f] where d is the offset of the camera in relation to the paper, d also absorbs any offset error in the gravity sensor
 * f are the inverse focal length of the camera in x and y. This if lx,ly is measured in pixels fx and fy will be in pixels.
 * 
 * x is the state we are trying to estimate:
 * x = [dx,dy,fx,fy]
 * 
 * z are the measurements where the center paper is in the sensor as pixels from center of sensor
 * z = [lx,ly]
 * 
 * F is the state transistion matrix which is unity matrix sinse the stuff we are trying to estimate are modeled as constant
 * F = [ones] //State transition matrix
 * 
 * Q is the noise affecting the state
 * Q = []
 * 
 * H is the state to measurement transform matrix, it is _not_ constant and takes values from the measure gravity sensor
 * H = [1 0 (gx) 0 0, 0 1 0 (gy)]
 * 
 * R is our noise model for the measurements
 * 
 * 
 * @author birkler
 *
 */
public class FOVKalmanFilter {
    // kinematics description
    private SimpleMatrix F;
    private SimpleMatrix Q;
    private SimpleMatrix H;

    // sytem state estimate
    private SimpleMatrix x;
    private SimpleMatrix P;
    static final float fovguess  = 45.0f; 

    private boolean isConfigured = false;
	private SimpleMatrix orientation;
	private int sensorWidth = 640;
	private int sensorHeight = 480;
    
    public FOVKalmanFilter() {
        this.F = SimpleMatrix.identity(4);
        this.Q = SimpleMatrix.diag(10.0,10.0,10.0,10.0); //We let the state deteriorate ever so slightly
        x = new SimpleMatrix(4,1);
        x.set(0,0, 0.0);
        x.set(1,0, 0.0);
        x.set(2,0, 1 / (640 * Math.tan(fovguess*Math.PI / 180)));
        x.set(3,0, 1 / (480 * Math.tan(fovguess*Math.PI / 180)));
        P = SimpleMatrix.diag(100,100,0.5,0.5); //We are very uncertain about the initial state
        orientation = new SimpleMatrix(3,1);
        H = new SimpleMatrix(2,4);
    }
    
    public void configure(int w, int h) {
    	sensorWidth = w;
    	sensorHeight = h;
        x.set(0,0, 0.0);
        x.set(1,0, 0.0);
        x.set(2,0, 1 / (sensorWidth * Math.tan(fovguess*Math.PI / 180)));
        x.set(3,0, 1 / (sensorHeight * Math.tan(fovguess*Math.PI / 180)));
        isConfigured = true;
    }
    
    public boolean isConfigured() {
    	return isConfigured;
    }

    public void predict() {
        // x = F x
        x = F.mult(x);
        
        // P = F P F' + Q
        P = F.mult(P).mult(F.transpose()).plus(Q);
    }
    
    public void updateOrientation(float[] val) {
    	orientation.set(0, 0, val[0]);
    	orientation.set(1, 0, val[1]);
    	orientation.set(2, 0, val[2]);
    	double norm = orientation.normF();
    	orientation = orientation.scale(1/norm);
        H.set(0,0,1.0);
        H.set(0,1,orientation.get(0));
        H.set(0,2,0.0);
        H.set(0,3,0.0);
        H.set(1,0,0.0);
        H.set(1,1,0.0);
        H.set(1,2,1.0);
        H.set(1,3,orientation.get(1));
    }

    public void update(float centerX, float centerY,float rx, float ry) {
        // a fast way to make the matrices usable by SimpleMatrix
        SimpleMatrix z = new SimpleMatrix(2,1,false,centerX,centerY);
        SimpleMatrix R = SimpleMatrix.diag(rx,ry);

        // y = z - H x
        SimpleMatrix y = z.minus(H.mult(x));

        // S = H P H' + R
        SimpleMatrix S = H.mult(P).mult(H.transpose()).plus(R);

        // K = PH'S^(-1)
        SimpleMatrix K = P.mult(H.transpose().mult(S.invert()));

        // x = x + Ky
        x = x.plus(K.mult(y));

        // P = (I-kH)P = P - KHP
        P = P.minus(K.mult(H).mult(P));
    }
    public double getDiagFOV() {
    	double arctan_val = Math.sqrt(x.get(2)*x.get(2) + x.get(3)*x.get(3))  / Math.sqrt(sensorHeight*sensorHeight + sensorWidth*sensorWidth);
    	return 2 * Math.atan(arctan_val);
    }

    public DenseMatrix64F getState() {
        return x.getMatrix();
    }

    public DenseMatrix64F getCovariance() {
        return P.getMatrix();
    }
}
