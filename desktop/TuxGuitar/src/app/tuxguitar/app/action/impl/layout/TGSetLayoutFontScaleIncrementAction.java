package app.tuxguitar.app.action.impl.layout;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.app.view.component.tab.TablatureEditor;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGSetLayoutFontScaleIncrementAction extends TGActionBase{

	public static final String NAME = "action.view.layout-increment-font-scale";

	private static final Float INCREMENT_VALUE = 0.1f;

	public TGSetLayoutFontScaleIncrementAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext tgActionContext) {
		Tablature tablature = TablatureEditor.getInstance(getContext()).getTablature();

		Float fontScale = tablature.getFontScale() + INCREMENT_VALUE;

		tgActionContext.setAttribute(TGSetLayoutFontScaleAction.ATTRIBUTE_FONT_SCALE, fontScale);

		TGActionManager.getInstance(getContext()).execute(TGSetLayoutFontScaleAction.NAME, tgActionContext);
	}
}
