package app.tuxguitar.app.action.impl.transport;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.app.transport.TGStartCountdown;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

public class TGSetStartCountdownSecondsAction extends TGActionBase {

	public static final String NAME = "action.transport.set-start-countdown-seconds";

	public static final String ATTRIBUTE_SECONDS = "seconds";

	public TGSetStartCountdownSecondsAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext context){
		Integer seconds = ((Integer) context.getAttribute(ATTRIBUTE_SECONDS));
		if( seconds != null ) {
			TGStartCountdown countdown = TGStartCountdown.getInstance(getContext());
			countdown.setSeconds(seconds);
			// choosing a length means wanting the countdown
			countdown.setEnabled(true);
		}
	}
}
