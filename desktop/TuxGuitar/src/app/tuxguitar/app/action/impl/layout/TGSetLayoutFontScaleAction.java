package app.tuxguitar.app.action.impl.layout;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.app.view.component.tab.TablatureEditor;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGSetLayoutFontScaleAction extends TGActionBase{

	public static final String NAME = "action.view.layout-set-font-scale";

	public static final String ATTRIBUTE_FONT_SCALE = "fontScale";

	public TGSetLayoutFontScaleAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext context) {
		Float fontScale = ((Float) context.getAttribute(ATTRIBUTE_FONT_SCALE));

		Tablature tablature = TablatureEditor.getInstance(getContext()).getTablature();
		tablature.scaleFont(fontScale);
	}
}
