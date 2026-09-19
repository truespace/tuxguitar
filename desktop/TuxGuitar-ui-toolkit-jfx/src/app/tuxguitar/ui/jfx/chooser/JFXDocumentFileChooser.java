package app.tuxguitar.ui.jfx.chooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.chooser.UIFileChooser;
import app.tuxguitar.ui.chooser.UIFileChooserFormat;
import app.tuxguitar.ui.chooser.UIFileChooserHandler;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.jfx.util.JFXPlatformUtil;
import app.tuxguitar.ui.jfx.widget.JFXWindow;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIListBoxSelect;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UITextField;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * File chooser for iOS, where javafx.stage.FileChooser is not available: browses the app
 * Documents folder, which the Files app shows as "On My iPad / TuxGuitar".
 */
public class JFXDocumentFileChooser implements UIFileChooser {

	private static final String PARENT_ENTRY = "..";

	private UIFactory uiFactory;
	private JFXWindow window;
	private int style;
	private String text;
	private File defaultPath;
	private List<UIFileChooserFormat> supportedFormats;

	private File rootDirectory;
	private File currentDirectory;
	private UILabel pathLabel;
	private UIListBoxSelect<File> fileList;
	private UITextField fileName;

	public JFXDocumentFileChooser(UIFactory uiFactory, JFXWindow window, int style) {
		this.uiFactory = uiFactory;
		this.window = window;
		this.style = style;
	}

	public void choose(final UIFileChooserHandler selectionHandler) {
		this.rootDirectory = JFXPlatformUtil.getIOSDocumentsDirectory();
		this.rootDirectory.mkdirs();
		this.currentDirectory = this.createInitialDirectory();

		final UITableLayout dialogLayout = new UITableLayout();
		final UIWindow dialog = this.uiFactory.createWindow(this.window, true, false);
		dialog.setLayout(dialogLayout);
		dialog.setText(this.text != null ? this.text : (this.isSave() ? "Save" : "Open"));

		this.pathLabel = this.uiFactory.createLabel(dialog);
		dialogLayout.set(this.pathLabel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		this.fileList = this.uiFactory.createListBoxSelect(dialog);
		this.fileList.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				onFileSelected();
			}
		});
		dialogLayout.set(this.fileList, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 480f, 320f, null);

		if( this.isSave() ) {
			this.fileName = this.uiFactory.createTextField(dialog);
			this.fileName.setText(this.createInitialFileName());
			dialogLayout.set(this.fileName, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		}

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = this.uiFactory.createPanel(dialog, false);
		buttons.setLayout(buttonsLayout);
		dialogLayout.set(buttons, 4, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_BOTTOM, true, false);

		final UIButton buttonOK = this.uiFactory.createButton(buttons);
		buttonOK.setText(this.isSave() ? "Save" : "Open");
		buttonOK.setDefaultButton();
		buttonOK.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				File file = getSelectedFile();
				if( file != null ) {
					dialog.dispose();
					selectionHandler.onSelectFile(file);
				}
			}
		});
		buttonsLayout.set(buttonOK, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);

		UIButton buttonCancel = this.uiFactory.createButton(buttons);
		buttonCancel.setText("Cancel");
		buttonCancel.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				dialog.dispose();
				selectionHandler.onSelectFile(null);
			}
		});
		buttonsLayout.set(buttonCancel, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);
		buttonsLayout.set(buttonCancel, UITableLayout.MARGIN_RIGHT, 0f);

		this.updateFileList();

		dialog.pack();

		UIRectangle parentBounds = this.window.getBounds();
		UIRectangle dialogBounds = dialog.getBounds();
		dialogBounds.getPosition().setX(Math.max(0, parentBounds.getX() + (parentBounds.getWidth() - dialogBounds.getWidth()) / 2f));
		dialogBounds.getPosition().setY(Math.max(0, parentBounds.getY() + (parentBounds.getHeight() - dialogBounds.getHeight()) / 2f));
		dialog.setBounds(dialogBounds);

		dialog.open();
	}

	private boolean isSave() {
		return (this.style == JFXFileChooser.STYLE_SAVE);
	}

	private void onFileSelected() {
		File selection = this.fileList.getSelectedValue();
		if( selection != null && selection.isDirectory() ) {
			// a single tap opens folders (and ".." goes back up)
			this.currentDirectory = selection;
			this.updateFileList();
		} else if( selection != null && this.fileName != null ) {
			this.fileName.setText(selection.getName());
		}
	}

	private File getSelectedFile() {
		if( this.isSave() ) {
			String name = this.fileName.getText().trim();
			if( name.isEmpty() ) {
				return null;
			}
			if( name.indexOf('.') < 0 ) {
				String extension = this.getDefaultExtension();
				if( extension != null ) {
					name = (name + "." + extension);
				}
			}
			return new File(this.currentDirectory, name);
		}
		File selection = this.fileList.getSelectedValue();
		return (selection != null && selection.isFile() ? selection : null);
	}

	private void updateFileList() {
		String relativePath = this.rootDirectory.toURI().relativize(this.currentDirectory.toURI()).getPath();
		this.pathLabel.setText("TuxGuitar/" + relativePath);

		this.fileList.removeItems();
		if(!this.currentDirectory.equals(this.rootDirectory) ) {
			this.fileList.addItem(new UISelectItem<File>(PARENT_ENTRY, this.currentDirectory.getParentFile()));
		}

		File[] files = this.currentDirectory.listFiles();
		if( files != null ) {
			Arrays.sort(files, new Comparator<File>() {
				public int compare(File f1, File f2) {
					if( f1.isDirectory() != f2.isDirectory() ) {
						return (f1.isDirectory() ? -1 : 1);
					}
					return f1.getName().compareToIgnoreCase(f2.getName());
				}
			});
			for(File file : files) {
				if( file.isHidden() ) {
					continue;
				}
				if( file.isDirectory() ) {
					this.fileList.addItem(new UISelectItem<File>(file.getName() + "/", file));
				} else if( this.isSupported(file) ) {
					this.fileList.addItem(new UISelectItem<File>(file.getName(), file));
				}
			}
		}
	}

	private boolean isSupported(File file) {
		List<String> extensions = this.getSupportedExtensions();
		if( extensions.isEmpty() ) {
			return true;
		}
		String name = file.getName().toLowerCase();
		for(String extension : extensions) {
			if( name.endsWith("." + extension.toLowerCase()) ) {
				return true;
			}
		}
		return false;
	}

	private List<String> getSupportedExtensions() {
		List<String> extensions = new ArrayList<String>();
		if( this.supportedFormats != null ) {
			for(UIFileChooserFormat supportedFormat : this.supportedFormats) {
				extensions.addAll(supportedFormat.getExtensions());
			}
		}
		return extensions;
	}

	private String getDefaultExtension() {
		List<String> extensions = this.getSupportedExtensions();
		return (extensions.isEmpty() ? null : extensions.get(0));
	}

	private File createInitialDirectory() {
		if( this.defaultPath != null ) {
			File directory = (this.defaultPath.isDirectory() ? this.defaultPath : this.defaultPath.getParentFile());
			if( directory != null && directory.isDirectory() && this.isInsideRoot(directory) ) {
				return directory;
			}
		}
		return this.rootDirectory;
	}

	private String createInitialFileName() {
		if( this.defaultPath != null && !this.defaultPath.isDirectory() ) {
			return this.defaultPath.getName();
		}
		return "";
	}

	private boolean isInsideRoot(File directory) {
		String root = this.rootDirectory.getAbsolutePath();
		String path = directory.getAbsolutePath();
		return (path.equals(root) || path.startsWith(root + File.separator));
	}

	public void setText(String text) {
		this.text = text;
	}

	public void setDefaultPath(File defaultPath) {
		this.defaultPath = defaultPath;
	}

	public void setSupportedFormats(List<UIFileChooserFormat> supportedFormats) {
		this.supportedFormats = supportedFormats;
	}
}
