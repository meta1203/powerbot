package org.powerbot.script.methods;

import java.awt.Point;
import java.awt.Rectangle;

import org.powerbot.client.Client;
import org.powerbot.script.wrappers.CollisionMap;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.LocalPath;
import org.powerbot.script.wrappers.Locatable;
import org.powerbot.script.wrappers.Targetable;
import org.powerbot.script.wrappers.Tile;
import org.powerbot.script.wrappers.TileMatrix;
import org.powerbot.script.wrappers.TilePath;

public class Movement extends MethodProvider {
	public static final int WIDGET_MAP = 1465;
	public static final int COMPONENT_MAP = 12;
	public static final int COMPONENT_RUN = 4;
	public static final int COMPONENT_RUN_ENERGY = 5;
	public static final int SETTING_RUN_ENABLED = 463;

	public Movement(MethodContext factory) {
		super(factory);
	}

	/**
	 * Creates a new tile path.
	 *
	 * @param tiles The array of tiles in the path.
	 * @return the generated {@link TilePath}
	 */
	public TilePath newTilePath(final Tile... tiles) {
		if (tiles == null) {
			throw new IllegalArgumentException("tiles are null");
		}
		return new TilePath(ctx, tiles);
	}

	/**
	 * Creates a local path in the current region.
	 *
	 * @param locatable the destination tile
	 * @return the generated {@link LocalPath}
	 */
	public LocalPath findPath(Locatable locatable) {
		if (locatable == null) {
			throw new IllegalArgumentException();
		}
		return new LocalPath(ctx, ctx.map, locatable);
	}

	/**
	 * Determines the current destination of the player.
	 *
	 * @return the {@link Tile} destination; or {@link Tile#NIL} if there is no destination
	 */
	public Tile getDestination() {
		Client client = ctx.getClient();
		if (client == null) {
			return Tile.NIL;
		}
		final int dX = client.getDestX(), dY = client.getDestY();
		if (dX == -1 || dY == -1) {
			return Tile.NIL;
		}
		return ctx.game.getMapBase().derive(dX, dY);
	}

	/**
	 * Steps towards the provided {@link Locatable}.
	 *
	 * @param locatable the locatable to step towards
	 * @return <tt>true</tt> if stepped; otherwise <tt>false</tt>
	 */
	public boolean stepTowards(final Locatable locatable) {
		Tile loc = locatable.getLocation();
		if (!loc.getMatrix(ctx).isOnMap()) {
			loc = getClosestOnMap(loc);
		}
		final Tile t = loc;
		return ctx.mouse.click(new Targetable() {
			private TileMatrix tile = t.getMatrix(ctx);

			@Override
			public Point getInteractPoint() {
				return tile.getMapPoint();
			}

			@Override
			public Point getNextPoint() {
				return tile.getMapPoint();
			}

			@Override
			public Point getCenterPoint() {
				return tile.getMapPoint();
			}

			@Override
			public boolean contains(final Point point) {
				Point p = tile.getMapPoint();
				Rectangle t = new Rectangle(p.x - 2, p.y - 2, 4, 4);
				return t.contains(point);
			}
		}, true);
	}

	/**
	 * Determines the closest tile on the map to the provided {@link Locatable}.
	 *
	 * @param locatable the {@link Locatable}
	 * @return the closest {@link Tile} on map to the provided {@link Locatable}
	 */
	public Tile getClosestOnMap(Locatable locatable) {
		Tile local = ctx.players.local().getLocation();
		Tile tile = locatable.getLocation();
		if (local == Tile.NIL || tile == Tile.NIL) {
			return Tile.NIL;
		}
		if (tile.getMatrix(ctx).isOnMap()) {
			return tile;
		}
		int x2 = local.x, y2 = local.y, x1 = tile.x, y1 = tile.y;
		int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
		int sx = (x1 < x2) ? 1 : -1, sy = (y1 < y2) ? 1 : -1;
		int off = dx - dy;
		for (; ; ) {
			Tile t = new Tile(x1, y1, local.plane);
			if (t.getMatrix(ctx).isOnMap()) {
				return t;
			}
			if (x1 == x2 && y1 == y2) {
				break;
			}
			int e2 = 2 * off;
			if (e2 > -dy) {
				off = off - dy;
				x1 = x1 + sx;
			}
			if (e2 < dx) {
				off = off + dx;
				y1 = y1 + sy;
			}
		}
		return Tile.NIL;
	}

	/**
	 * Alters the running state.
	 *
	 * @param run <tt>true</tt> to run; otherwise <tt>false</tt>
	 * @return <tt>true</tt> if the state was successfully changed; otherwise <tt>false</tt>
	 */
	public boolean setRunning(final boolean run) {
		if (isRunning() != run) {
			final Component c = ctx.widgets.get(WIDGET_MAP, COMPONENT_RUN);
			if (c != null && c.click(true)) {
				for (int i = 0; i < 20 && isRunning() != run; i++) {
					sleep(100, 200);
				}
			}
		}
		return isRunning() == run;
	}

	/**
	 * Determines if the player is currently set to run.
	 *
	 * @return <tt>true</tt> if set to be running; otherwise <tt>false</tt>
	 */
	public boolean isRunning() {
		return ctx.settings.get(SETTING_RUN_ENABLED) == 0x1;
	}

	/**
	 * Determines the current energy level of the player.
	 *
	 * @return the current energy level
	 */
	public int getEnergyLevel() {
		final Component c = ctx.widgets.get(WIDGET_MAP, COMPONENT_RUN_ENERGY);
		if (c != null && c.isValid()) {
			try {
				return Integer.parseInt(c.getText().replace('%', ' ').trim());
			} catch (final NumberFormatException ignored) {
			}
		}
		return 0;
	}

	public CollisionMap getCollisionMap() {
		return getCollisionMap(ctx.game.getPlane());
	}

	public CollisionMap getCollisionMap(int plane) {
		CollisionMap[] planes = ctx.map.getCollisionMaps();
		if (plane < 0 || plane >= planes.length) {
			return new CollisionMap(0, 0);
		}
		return planes[plane];
	}

	/**
	 * Gets the distance between two places in the loaded game region.
	 *
	 * @param _start the start position
	 * @param _end   the end position
	 * @return the computed path distance
	 */
	public int getDistance(Locatable _start, Locatable _end) {
		Tile start, end;
		if (_start == null || _end == null) {
			return -1;
		}
		start = _start.getLocation();
		end = _end.getLocation();

		Tile base = ctx.game.getMapBase();
		if (base == Tile.NIL || start == Tile.NIL || end == Tile.NIL) {
			return -1;
		}
		start = start.derive(-base.x, -base.y);
		end = end.derive(-base.x, -base.y);

		int startX = start.getX(), startY = start.getY();
		int endX = end.getX(), endY = end.getY();
		return ctx.map.getDistance(startX, startY, endX, endY, ctx.game.getPlane());
	}

	/**
	 * Determines if the the end position is reachable from the start position.
	 *
	 * @param _start the start position
	 * @param _end   the end position
	 * @return <tt>true</tt> if the end is reachable; otherwise <tt>false</tt>
	 */
	public boolean isReachable(Locatable _start, Locatable _end) {
		Tile start, end;
		if (_start == null || _end == null) {
			return false;
		}
		start = _start.getLocation();
		end = _end.getLocation();

		Tile base = ctx.game.getMapBase();
		if (base == Tile.NIL || start == Tile.NIL || end == Tile.NIL) {
			return false;
		}
		start = start.derive(-base.x, -base.y);
		end = end.derive(-base.x, -base.y);

		int startX = start.getX(), startY = start.getY();
		int endX = end.getX(), endY = end.getY();
		return ctx.map.getPath(startX, startY, endX, endY, ctx.game.getPlane()).length > 0;
	}
}
