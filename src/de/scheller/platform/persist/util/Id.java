package de.scheller.platform.persist.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author kandzia
 */
public class Id
{
	private static long lastTimestamp = 0;
	private static char prefix;

	public static synchronized String next() {
		long ts = System.currentTimeMillis()/100;
		if (ts <= lastTimestamp) ts = lastTimestamp + 1;
		lastTimestamp = ts;
		String id = Long.toString(ts,36);
		if (prefix!=0) id = prefix + id;
		return id;
	}

	public static long getTimestamp(String id) {
		return Long.parseLong(id,36)*100;
	}

	public static void periodicallySaveIdState(File stateFile) {
		IdState.init(stateFile);
	}

	private static class IdState implements Runnable {
		static ScheduledExecutorService executor;
		static File stateFile;
		static long lastSavedTimestamp;
		static SimpleDateFormat sdf = new SimpleDateFormat("'written at 'yyyy-MM-dd HH:mm:ss");

		static void init(File stateFile) {
			if (IdState.executor!=null)
				return;
			IdState.stateFile = stateFile;
			int period = 60;
			IdState.executor = Executors.newSingleThreadScheduledExecutor();
			IdState.executor.scheduleAtFixedRate(new IdState(),period,period,TimeUnit.SECONDS);
			if (stateFile.exists()) {
				long now = System.currentTimeMillis();
				try (FileInputStream is = new FileInputStream(stateFile)) {
					Properties p = new Properties();
					p.load(is);
					String timestamp = p.getProperty("timestamp");
					long ts = timestamp!=null ? Long.parseLong(timestamp) : 0l;
					ts += 2 * period * 1000;
					lastSavedTimestamp = Math.max(ts,now);
					Id.lastTimestamp = lastSavedTimestamp/100;
					String prefix = p.getProperty("prefix");
					if (prefix!=null)
						Id.prefix = prefix.charAt(0);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		public void run() {
			long ts = Id.lastTimestamp*100;
			if (ts <= lastSavedTimestamp)
				return;
			try (FileOutputStream os = new FileOutputStream(stateFile)) {
				Properties p = new Properties();
				p.setProperty("timestamp",Long.toString(ts));
				if (Id.prefix!=0)
					p.setProperty("prefix",""+Id.prefix);
				p.store(os,null);
				lastSavedTimestamp = ts;
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
