/*
 * Copyright 2008-2009, David Karnok 
 * The file is part of the Open Imperium Galactica project.
 * 
 * The code should be distributed under the LGPL license.
 * See http://www.gnu.org/licenses/lgpl.html for details.
 */

package hu.openig.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The road tile directional type.
 * @author karnokd, 2009.05.21.
 * @version $Revision 1.0$
 */
public enum RoadType {
	/** X axis linear road. _ */
	HORIZONTAL(1, Sides.LEFT | Sides.RIGHT),
	/** Top-right corner. |_ */
	TOP_TO_RIGHT(2, Sides.TOP | Sides.RIGHT),
	/** Top to left corner. _| */
	TOP_TO_LEFT(3, Sides.LEFT | Sides.TOP),
	/** Left to bottom corner. ^^| */
	LEFT_TO_BOTTOM(4, Sides.LEFT | Sides.BOTTOM),
	/** Right to bottom. |^^*/
	RIGHT_TO_BOTTOM(5, Sides.BOTTOM | Sides.RIGHT),
	/** Vertical. | */
	VERTICAL(6, Sides.TOP | Sides.BOTTOM),
	/** Horizontal bottom. -,- */
	HORIZONTAL_BOTTOM(7, Sides.LEFT | Sides.BOTTOM | Sides.RIGHT),
	/** Vertical to right. |- */
	VERTICAL_RIGHT(8, Sides.TOP | Sides.BOTTOM | Sides.RIGHT),
	/** Horizontal top. -'- */
	HORIZONTAL_TOP(9, Sides.LEFT | Sides.TOP | Sides.RIGHT),
	/** Vertical left. -| */
	VERTICAL_LEFT(10, Sides.LEFT | Sides.TOP | Sides.BOTTOM),
	/** Cross. -|- */
	CROSS(11, Sides.LEFT | Sides.TOP | Sides.BOTTOM | Sides.RIGHT)
	;
	/**
	 * The road sides constants.
	 * @author karnokd, 2009.05.23.
	 * @version $Revision 1.0$
	 */
	public final class Sides {
		/** Constructor. */
		private Sides() {
			// constant class
		}
		/** Road has a left exit. */
		public static final int LEFT = 8;
		/** Road has a top exit. */
		public static final int TOP = 4;
		/** Road has a bottom exit. */
		public static final int BOTTOM = 2;
		/** Road has a right exit. */
		public static final int RIGHT = 1;
	}
	/** The road type index. */
	public final int index;
	/** The road pattern. Combined from the constants below */
	public final int pattern;
	/**
	 * Constructor.
	 * @param index the road type index.
	 * @param pattern the road pattern
	 */
	RoadType(int index, int pattern) {
		this.index = index;
		this.pattern = pattern;
	}
	/**
	 * The road pattern to road type map.
	 */
	private static final Map<Integer, RoadType> MAP;
	/** Initialize MAP. */
	static {
		MAP = new HashMap<Integer, RoadType>();
		for (RoadType rt : values()) {
			MAP.put(rt.pattern, rt);
		}
	}
	/**
	 * Returns the road type belonging to the
	 * given pattern.
	 * @param pattern the pattern composed of Sides.* constants
	 * @return the road type
	 */
	public static RoadType get(int pattern) {
		return MAP.get(pattern);
	}
}