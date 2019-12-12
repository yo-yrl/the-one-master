package routing;

import core.Settings;

public class CNRouter extends ActiveRouter {

	public CNRouter(Settings s) {
		super(s);
		// TODO Auto-generated constructor stub
	}

	public CNRouter(CNRouter r) {
		super(r);
	}

	@Override
	public MessageRouter replicate() {
		CNRouter r = new CNRouter(this);
		return r;
	}

}
