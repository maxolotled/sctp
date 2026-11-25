package com.snailtools.shoplogger.gui.data;

/** Mirrors GET /mod/join — see worker.js's handleModJoin. id changes whenever an admin saves a new popup. */
public class ModPopup {
	public boolean enabled;
	public String id;
	public String heading;
	public String message;
	public String buttonLabel;
	public String url;
}
