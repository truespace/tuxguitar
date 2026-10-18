package app.tuxguitar.app.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * "tuxguitar:" links, which let a web page (or any other application) open a file in TuxGuitar.
 *
 * The path may be absolute, or relative to a base folder: the Documents folder of the app on
 * iOS, the home folder on the desktop. These forms are accepted:
 *
 *   tuxguitar:///Users/someone/songs/song.gp5
 *   tuxguitar://open?path=songs/song.gp5
 *   tuxguitar://songs/song.gp5
 */
public class TGSchemeUrl {

	public static final String SCHEME = "tuxguitar";

	private static final String SCHEME_PREFIX = (SCHEME + ":");
	private static final String PATH_PARAMETER = "path";
	private static final String ENCODING = "UTF-8";

	private TGSchemeUrl() {
		super();
	}

	public static boolean isSchemeUrl(String text) {
		return (text != null && text.toLowerCase().startsWith(SCHEME_PREFIX));
	}

	/**
	 * File URL the link points to, or null when it carries no path.
	 */
	public static URL toFileUrl(String text, File baseDirectory) throws URISyntaxException, MalformedURLException, UnsupportedEncodingException {
		String path = findPath(new URI(text));
		if( path == null || path.length() == 0 ) {
			return null;
		}

		File file = new File(path);
		if(!file.isAbsolute() && baseDirectory != null ) {
			file = new File(baseDirectory, path);
		}
		return file.toURI().toURL();
	}

	private static String findPath(URI uri) throws UnsupportedEncodingException {
		String parameter = findPathParameter(uri.getRawQuery());
		if( parameter != null ) {
			return parameter;
		}

		String path = uri.getPath();
		if( path != null && path.length() > 0 ) {
			return path;
		}

		// tuxguitar://songs/song.gp5 : the first segment is read as the host of the URI
		if( uri.getHost() != null ) {
			return uri.getHost();
		}

		// opaque form, e.g. tuxguitar:songs/song.gp5
		return uri.getSchemeSpecificPart();
	}

	private static String findPathParameter(String query) throws UnsupportedEncodingException {
		if( query != null ) {
			String[] parameters = query.split("&");
			for(int i = 0; i < parameters.length; i ++) {
				int separator = parameters[i].indexOf('=');
				if( separator > 0 && PATH_PARAMETER.equals(parameters[i].substring(0, separator)) ) {
					return URLDecoder.decode(parameters[i].substring(separator + 1), ENCODING);
				}
			}
		}
		return null;
	}
}
