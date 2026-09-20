package app.tuxguitar.ui.jfx.widget;

import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Title bar and resize handle of the dialogs on iOS, where windows carry no decoration:
 * without them a dialog cannot be moved, and its buttons are out of reach as soon as it
 * extends past the bottom of the screen.
 */
public class JFXWindowCaption {

	public static final float HEIGHT = 34f;

	private static final float CLOSE_BUTTON_WIDTH = 44f;
	private static final float RESIZE_HANDLE_SIZE = 32f;
	private static final float MINIMUM_SIZE = 160f;

	private final JFXWindow window;
	private final Pane caption;
	private final Label title;
	private final Label closeButton;
	private final Region resizeHandle;

	private double dragOffsetX;
	private double dragOffsetY;

	public JFXWindowCaption(JFXWindow window) {
		this.window = window;

		this.title = new Label();
		this.title.getStyleClass().add("JFXWindowCaptionTitle");
		this.title.textProperty().bind(window.getStage().titleProperty());
		this.title.setLayoutY(0);
		this.title.setPrefHeight(HEIGHT);

		this.closeButton = new Label("✕");
		this.closeButton.getStyleClass().add("JFXWindowCaptionClose");
		this.closeButton.setPrefSize(CLOSE_BUTTON_WIDTH, HEIGHT);
		this.closeButton.setLayoutY(0);
		this.closeButton.setOnMouseReleased(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent event) {
				JFXWindowCaption.this.window.close();
			}
		});

		this.caption = new Pane(this.title, this.closeButton);
		this.caption.getStyleClass().add("JFXWindowCaption");
		this.caption.setPrefHeight(HEIGHT);
		this.caption.setLayoutY(0);
		this.caption.setOnMousePressed(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent event) {
				JFXWindowCaption.this.onDragStart(event);
			}
		});
		this.caption.setOnMouseDragged(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent event) {
				JFXWindowCaption.this.onDragMove(event);
			}
		});

		this.resizeHandle = new Region();
		this.resizeHandle.getStyleClass().add("JFXWindowResizeHandle");
		this.resizeHandle.setPrefSize(RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
		this.resizeHandle.setOnMouseDragged(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent event) {
				JFXWindowCaption.this.onResize(event);
			}
		});

		window.getControl().getChildren().addAll(this.caption, this.resizeHandle);

		this.layout();
	}

	/**
	 * Keeps the caption across the top of the window and the handle in its bottom right corner.
	 */
	public void layout() {
		double width = this.window.getStage().getScene().getWidth();
		double height = this.window.getStage().getScene().getHeight();

		this.caption.resize(width, HEIGHT);
		this.title.resize(Math.max(0, width - CLOSE_BUTTON_WIDTH), HEIGHT);
		this.closeButton.setLayoutX(Math.max(0, width - CLOSE_BUTTON_WIDTH));
		this.closeButton.resize(CLOSE_BUTTON_WIDTH, HEIGHT);

		this.resizeHandle.resize(RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
		this.resizeHandle.setLayoutX(Math.max(0, width - RESIZE_HANDLE_SIZE));
		this.resizeHandle.setLayoutY(Math.max(0, height - RESIZE_HANDLE_SIZE));
		this.resizeHandle.toFront();
	}

	private void onDragStart(MouseEvent event) {
		Stage stage = this.window.getStage();

		this.dragOffsetX = (stage.getX() - event.getScreenX());
		this.dragOffsetY = (stage.getY() - event.getScreenY());
	}

	private void onDragMove(MouseEvent event) {
		Stage stage = this.window.getStage();
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();

		// at least the caption stays on the screen, so the window can always be dragged back
		double maximumX = (screen.getMaxX() - Math.min(stage.getWidth(), CLOSE_BUTTON_WIDTH));
		double maximumY = (screen.getMaxY() - HEIGHT);
		double x = (event.getScreenX() + this.dragOffsetX);
		double y = (event.getScreenY() + this.dragOffsetY);

		stage.setX(Math.min(Math.max(screen.getMinX() - (stage.getWidth() - CLOSE_BUTTON_WIDTH), x), maximumX));
		stage.setY(Math.min(Math.max(screen.getMinY(), y), maximumY));
	}

	private void onResize(MouseEvent event) {
		Stage stage = this.window.getStage();
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();

		double width = (event.getScreenX() - stage.getX());
		double height = (event.getScreenY() - stage.getY());

		stage.setWidth(Math.min(Math.max(MINIMUM_SIZE, width), screen.getWidth()));
		stage.setHeight(Math.min(Math.max(MINIMUM_SIZE, height), screen.getHeight()));
	}
}
