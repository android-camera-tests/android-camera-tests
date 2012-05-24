package se.birkler.opencvcalibrate.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import se.birkler.opencvcalibrate.opencvutil.CaptureDataAction;
import se.birkler.opencvcalibrate.util.XLog;
import android.content.Context;


public class CalibrationCaptureDataWriteToDisk extends PictureCaptureData implements CaptureDataAction   {
	public CameraCalibrationData data;
	private File mToFile;

	CalibrationCaptureDataWriteToDisk(File toFile, CameraCalibrationData data) {
		super();
		mToFile = toFile;
		this.data = data;
	}
	
	void writeFile(Context context) {
		ObjectMapper mapper = new ObjectMapper();
		//mapper.configure(Featur\\\\\\\\\\\\\\]]]tate)configure(SerializationConfig.Feature., false);
		mapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
		mapper.disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
		
		try {
			byte json[] = mapper.writeValueAsBytes(this);
			OutputStream os;
			mToFile.createNewFile();
			os = new FileOutputStream(mToFile);
			os.write(json);
			os.close();
			
		} catch (FileNotFoundException e) {
			XLog.e("file not found "+mToFile.toString(), e);
		} catch (IOException e) {
			XLog.e("cannot write jpeg "+mToFile.toString(), e);
		}
	}

	@Override
	public boolean execute(Context context) {
		writeFile(context);
		return true;
	}
}
