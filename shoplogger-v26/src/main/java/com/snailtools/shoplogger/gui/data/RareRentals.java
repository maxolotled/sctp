package com.snailtools.shoplogger.gui.data;

import java.util.List;

/** Shape of GET /rare-items — per-world lists of item names rentable at /pw RentARare (Firefly) or /pw tool_library (Honeybee). */
public class RareRentals {
	public List<String> firefly;
	public List<String> honeybee;
}
