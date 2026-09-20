package app.tuxguitar.gluon;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * In-memory java.util.prefs backend for the native image builds: the platform backends rely on
 * JNI functions native-image does not implement (JVM_ArrayCopy on macOS), and Gervill reads its
 * stored settings from Preferences when opening the synthesizer. TuxGuitar keeps its own
 * settings in its configuration files, so nothing needs to be persisted here.
 */
public class TGGluonPreferencesFactory implements PreferencesFactory {

	// the factory itself may be created while building the image (GraalVM 22.1 initializes
	// java.util.prefs at build time): the roots are only created at run time
	private Preferences systemRoot;
	private Preferences userRoot;

	public synchronized Preferences systemRoot() {
		if( this.systemRoot == null ) {
			this.systemRoot = new MemoryPreferences(null, "");
		}
		return this.systemRoot;
	}

	public synchronized Preferences userRoot() {
		if( this.userRoot == null ) {
			this.userRoot = new MemoryPreferences(null, "");
		}
		return this.userRoot;
	}

	private static class MemoryPreferences extends AbstractPreferences {

		private final Map<String, String> values = new HashMap<String, String>();
		private final Map<String, MemoryPreferences> children = new HashMap<String, MemoryPreferences>();

		MemoryPreferences(MemoryPreferences parent, String name) {
			super(parent, name);
		}

		protected void putSpi(String key, String value) {
			this.values.put(key, value);
		}

		protected String getSpi(String key) {
			return this.values.get(key);
		}

		protected void removeSpi(String key) {
			this.values.remove(key);
		}

		protected void removeNodeSpi() throws BackingStoreException {
			// nothing stored
		}

		protected String[] keysSpi() throws BackingStoreException {
			return this.values.keySet().toArray(new String[0]);
		}

		protected String[] childrenNamesSpi() throws BackingStoreException {
			return this.children.keySet().toArray(new String[0]);
		}

		protected AbstractPreferences childSpi(String name) {
			MemoryPreferences child = this.children.get(name);
			if( child == null ) {
				child = new MemoryPreferences(this, name);
				this.children.put(name, child);
			}
			return child;
		}

		protected void syncSpi() throws BackingStoreException {
			// nothing stored
		}

		protected void flushSpi() throws BackingStoreException {
			// nothing stored
		}
	}
}
