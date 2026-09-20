package app.tuxguitar.app.action.impl.layout;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGSetLayoutFontScaleResetAction extends TGActionBase{

	public static final String NAME = "action.view.layout-reset-font-scale";

	public TGSetLayoutFontScaleResetAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext tgActionContext) {
		tgActionContext.setAttribute(TGSetLayoutFontScaleAction.ATTRIBUTE_FONT_SCALE, Tablature.DEFAULT_FONT_SCALE);

		TGActionManager.getInstance(getContext()).execute(TGSetLayoutFontScaleAction.NAME, tgActionContext);
	}
}
