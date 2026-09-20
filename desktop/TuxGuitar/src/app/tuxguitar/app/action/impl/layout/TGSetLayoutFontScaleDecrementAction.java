package app.tuxguitar.app.action.impl.layout;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.app.view.component.tab.TablatureEditor;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGSetLayoutFontScaleDecrementAction extends TGActionBase{

	public static final String NAME = "action.view.layout-decrement-font-scale";

	private static final Float DECREMENT_VALUE = 0.1f;

	public TGSetLayoutFontScaleDecrementAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext tgActionContext) {
		Tablature tablature = TablatureEditor.getInstance(getContext()).getTablature();

		Float fontScale = tablature.getFontScale() - DECREMENT_VALUE;

		tgActionContext.setAttribute(TGSetLayoutFontScaleAction.ATTRIBUTE_FONT_SCALE, fontScale);

		TGActionManager.getInstance(getContext()).execute(TGSetLayoutFontScaleAction.NAME, tgActionContext);
	}
}
