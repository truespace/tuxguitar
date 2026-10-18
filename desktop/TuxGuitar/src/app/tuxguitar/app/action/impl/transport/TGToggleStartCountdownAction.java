package app.tuxguitar.app.action.impl.transport;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.app.transport.TGStartCountdown;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGToggleStartCountdownAction extends TGActionBase {

	public static final String NAME = "action.transport.toggle-start-countdown";

	public TGToggleStartCountdownAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext context){
		TGStartCountdown countdown = TGStartCountdown.getInstance(getContext());
		countdown.setEnabled(!countdown.isEnabled());
	}
}
