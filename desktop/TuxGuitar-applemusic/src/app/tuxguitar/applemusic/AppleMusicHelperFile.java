package app.tuxguitar.applemusic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import app.tuxguitar.util.TGUserFileUtils;

/**
 * The helper travels inside the plugin, where it cannot be executed: it is written next to the
 * configuration of TuxGuitar the first time it is needed.
 */
public class AppleMusicHelperFile {

	private static final String RESOURCE = ("applemusic/" + AppleMusicBridge.HELPER_NAME);
	private static final int BUFFER_SIZE = 65536;

	private AppleMusicHelperFile() {
		super();
	}

	public static File find() throws IOException {
		File folder = new File(TGUserFileUtils.PATH_USER_CONFIG, "applemusic");
		File helper = new File(folder, AppleMusicBridge.HELPER_NAME);

		InputStream resource = AppleMusicHelperFile.class.getClassLoader().getResourceAsStream(RESOURCE);
		if( resource == null ) {
			if( helper.isFile() ) {
				return helper;
			}
			throw new IOException("the plugin carries no helper for this platform");
		}

		try {
			TGUserFileUtils.tryCreateDirectory(folder);
			copy(resource, helper);
			if(!helper.setExecutable(true, true) ) {
				throw new IOException("could not make the helper executable: " + helper.getAbsolutePath());
			}
		} finally {
			resource.close();
		}
		return helper;
	}

	private static void copy(InputStream from, File to) throws IOException {
		OutputStream out = new FileOutputStream(to);
		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			int count = 0;
			while( (count = from.read(buffer)) > 0 ) {
				out.write(buffer, 0, count);
			}
		} finally {
			out.close();
		}
	}
}
