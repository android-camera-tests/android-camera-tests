package com.sizetool.samplecapturer.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.os.Debug;
import android.util.Log;

public class XLog {
	public enum Level {
		V(1), D(2), I(3), W(4), E(5);

		private int value;

		private Level(int value) {
			this.value = value;
		}

		int getValue() {
			return value;
		}
	};

	public final static boolean WAIT_FOR_DEBUGGER = false;

	static private Level traceLevel() {
		if (WAIT_FOR_DEBUGGER) {
			return Level.V;
		} else {
			return Level.D;
		}
	}

	private static final String TAG = "sizetool";

	private static Level sLevel = traceLevel();

	private static final int MAX_LOG_LINES = 250;
	private static final LinkedList<String> sLogBuffer = new LinkedList<String>();

	private static class LogContext {
		LogContext(StackTraceElement element) {
			// this.className = element.getClassName();
			this.simpleClassName = getSimpleClassName(element.getClassName());
			this.methodName = element.getMethodName();
			this.lineNumber = element.getLineNumber();
		}

		// String className;
		String simpleClassName;
		String methodName;
		int lineNumber;
	}

	private static LogContext getContext() {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		StackTraceElement element = trace[6]; // frame below us; the caller
		LogContext context = new LogContext(element);
		return context;
	}

	private static final String getMessage(String s) {
		LogContext c = getContext();
		String msg = c.simpleClassName + "." + c.methodName + "@" + c.lineNumber + ": " + s;
		return msg;
	}

	private static String getSimpleClassName(String className) {
		int i = className.lastIndexOf(".");
		if (i == -1) {
			return className;
		}
		return className.substring(i + 1);
	}

	public static Level getLevel() {
		return sLevel;
	}

	public static void setLevel(Level l) {
		sLevel = l;
	}

	public static void writeLogBufferToFile(File logFile) {
		ZipOutputStream zos = null;
		try {
			zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(logFile)));
			byte[] newLineBytes = "\n".getBytes();
			zos.putNextEntry(new ZipEntry("log.txt"));
			synchronized (sLogBuffer) {
				for (String lines : sLogBuffer) {
					zos.write(lines.getBytes());
					zos.write(newLineBytes);
				}
			}
			zos.closeEntry();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				zos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void v(String msg) {
		log(Level.V, msg);
	}

	public static void v(String msg, Throwable t) {
		log(Level.V, msg, t);
	}

	public static void d(String msg) {
		log(Level.D, msg);
	}

	public static void d(String msg, Throwable t) {
		log(Level.D, msg, t);
	}

	public static void i(String msg) {
		log(Level.I, msg);
	}

	public static void i(String msg, Throwable t) {
		log(Level.I, msg, t);
	}

	public static void w(String msg) {
		log(Level.W, msg);
	}

	public static void w(String msg, Throwable t) {
		log(Level.W, msg, t);
	}

	public static void e(String msg) {
		log(Level.E, msg);
	}

	public static void e(String msg, Throwable t) {
		log(Level.E, msg, t);
	}

	public static void h() {
		if (sLevel.getValue() > Level.V.getValue()) {
			return;
		}

		Double allocated = Debug.getNativeHeapAllocatedSize() / 1048576.0;
		Double available = Debug.getNativeHeapSize() / 1048576.0;
		Double free = Debug.getNativeHeapFreeSize() / 1048576.0;
		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		df.setMinimumFractionDigits(2);

		log(Level.I,
						"HEAP allocated: " + df.format(Runtime.getRuntime().totalMemory() / 1048576.0) + "MB of "
										+ df.format(Runtime.getRuntime().maxMemory() / 1048576.0) + "MB ("
										+ df.format((double) Runtime.getRuntime().freeMemory() / 1048576.0)
										+ "MB free)");
		log(Level.I,
						"HEAP native allocated: " + df.format(allocated) + "MB of " + df.format(available) + "MB ("
										+ df.format(free) + "MB free)");
	}

	private static final void trimLogBuffer() {
		synchronized (sLogBuffer) {
			while (sLogBuffer.size() > MAX_LOG_LINES) {
				sLogBuffer.remove();
			}
		}
	}

	private static final void log(Level level, String msg) {
		if (sLevel.getValue() <= level.getValue()) {
			msg = getMessage(msg);
			switch (level) {
			case V:
				Log.v(TAG, msg);
				break;
			case D:
				Log.d(TAG, msg);
				break;
			case I:
				Log.i(TAG, msg);
				break;
			case W:
				Log.w(TAG, msg);
				break;
			case E:
				Log.e(TAG, msg);
				break;
			}
			synchronized (sLogBuffer) {
				sLogBuffer.add(msg);
				trimLogBuffer();
			}
		}
	}

	private static final void log(Level level, String msg, Throwable t) {
		if (sLevel.getValue() <= level.getValue()) {
			msg = getMessage(msg);
			switch (level) {
			case V:
				Log.v(TAG, msg, t);
				break;
			case D:
				Log.d(TAG, msg, t);
				break;
			case I:
				Log.i(TAG, msg, t);
				break;
			case W:
				Log.w(TAG, msg, t);
				break;
			case E:
				Log.e(TAG, msg, t);
				break;
			}
			synchronized (sLogBuffer) {
				sLogBuffer.add(msg);
				sLogBuffer.add(Log.getStackTraceString(t));
				trimLogBuffer();
			}
		}
	}
}
