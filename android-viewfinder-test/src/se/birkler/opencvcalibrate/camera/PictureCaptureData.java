package se.birkler.opencvcalibrate.camera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.codehaus.jackson.map.ObjectMapper;

import se.birkler.opencvcalibrate.util.XLog;
import android.content.Context;



public abstract class PictureCaptureData extends CaptureData {

	//protected abstract String writeXml();
	
	public void writeFile(Context context, File file) {
		ObjectMapper mapper = new ObjectMapper();
		//mapper.configure(Featur\\\\\\\\\\\\\\]]]tate)configure(SerializationConfig.Feature., false);
		try {
			byte json[] = mapper.writeValueAsBytes(this);
			ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
			GZIPOutputStream zos = new GZIPOutputStream(bos);
			zos.write(json);
			bos.close();
			final byte magic[] = {'J','B','0','1',0};
			ByteBuffer lengthbuf = ByteBuffer.allocateDirect(4);
			lengthbuf.putInt(bos.size());
			
			OutputStream os;
			file.createNewFile();
			os = new FileOutputStream(file);
			os.write(mData);
			os.write(magic);
			os.write(lengthbuf.array());
			bos.writeTo(os);
			os.close();
		} catch (FileNotFoundException e) {
			XLog.e("file not found "+file.toString(), e);
		} catch (IOException e) {
			XLog.e("cannot write jpeg "+file.toString(), e);
		}
	}

}
