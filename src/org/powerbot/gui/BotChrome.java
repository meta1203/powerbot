package org.powerbot.gui;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.powerbot.Configuration;
import org.powerbot.bot.Bot;
import org.powerbot.bot.SelectiveEventQueue;
import org.powerbot.gui.component.BotLocale;
import org.powerbot.gui.component.BotMenuBar;
import org.powerbot.gui.component.BotOverlay;
import org.powerbot.gui.component.BotPanel;
import org.powerbot.service.UpdateCheck;
import org.powerbot.util.Ini;
import org.powerbot.util.OSXAdapt;
import org.powerbot.util.Tracker;
import org.powerbot.util.io.CryptFile;
import org.powerbot.util.io.Resources;

/**
 * @author Paris
 */
public class BotChrome extends JFrame implements Closeable {
	private static final Logger log = Logger.getLogger(BotChrome.class.getName());
	public static final int PANEL_MIN_WIDTH = 800, PANEL_MIN_HEIGHT = 600;
	private static final long serialVersionUID = -5535364874897541810L;

	private static BotChrome instance;
	private final CryptFile cache;
	private Bot bot;
	public BotPanel panel;
	public final BotOverlay overlay;
	public final BotMenuBar menuBar;

	private BotChrome() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (final Exception ignored) {
		}

		setTitle(Configuration.NAME + (Configuration.BETA ? " " + BotLocale.BETA : ""));
		setIconImage(Resources.getImage(Resources.Paths.ICON));
		setBackground(Color.BLACK);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		setFocusTraversalKeysEnabled(false);

		setJMenuBar(menuBar = new BotMenuBar(this));

		panel = new BotPanel();
		add(panel);
		SelectiveEventQueue.getInstance().setBlocking(false);

		log.log(Level.INFO, "", "Starting...");
		pack();
		setMinimumSize(getSize());
		cache = new CryptFile("window-cache.1.ini", false, getClass());
		setSize(getWindowCache());
		setLocationRelativeTo(getParent());
		setVisible(true);
		new OSXAdapt(this).run();

		Tracker.getInstance().trackPage("", getTitle());

		overlay = new BotOverlay(this);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(final ComponentEvent e) {
				overlay.adjustSize();
			}

			@Override
			public void componentMoved(final ComponentEvent e) {
				overlay.adjustSize();
			}
		});

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				close();
			}

			@Override
			public void windowDeiconified(final WindowEvent e) {
				if (overlay.isVisible()) {
					overlay.setVisible(false);
					overlay.setVisible(true);
				}
			}
		});

		Bot bot = null;
		if (new UpdateCheck().call()) {
			setTitle("RuneScape");
			bot = new Bot(this);
			new Thread(bot.threadGroup, bot).start();
		}
		this.bot = bot;

		System.gc();
	}

	public static synchronized BotChrome getInstance() {
		if (instance == null) {
			instance = new BotChrome();
		}
		return instance;
	}

	public Bot getBot() {
		return bot;
	}

	public static void openURL(final String url) {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			return;
		}
		final URI uri;
		try {
			uri = new URI(url);
		} catch (final URISyntaxException ignored) {
			return;
		}
		try {
			Desktop.getDesktop().browse(uri);
		} catch (final IOException ignored) {
		}
	}

	private void saveWindowCache() {
		OutputStream out = null;
		try {
			out = cache.getOutputStream();
			new Ini().get().put("w", getWidth()).put("h", getHeight()).parent().write(out);
		} catch (final IOException ignored) {
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (final IOException ignored) {
				}
			}
		}
	}

	private Dimension getWindowCache() {
		final Dimension d = getSize();

		if (!cache.exists()) {
			return d;
		}

		InputStream in = null;
		try {
			in = cache.getInputStream();
			final Ini.Member t = new Ini().read(in).get();
			return new Dimension(t.getInt("w", d.width), t.getInt("h", d.height));
		} catch (final IOException ignored) {
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (final IOException ignored) {
				}
			}
		}

		return d;
	}

	@Override
	public void close() {
		log.info("Shutting down");

		final int s = getExtendedState();
		final boolean maxed = (s & Frame.MAXIMIZED_VERT) == Frame.MAXIMIZED_VERT || (s & Frame.MAXIMIZED_HORIZ) == Frame.MAXIMIZED_HORIZ;

		if (!maxed) {
			saveWindowCache();
		}

		setVisible(false);
		dispose();
		System.exit(0);
	}

	public void display(final Bot bot) {
		remove(panel);
		if (this.bot != null) {
			remove(bot.applet);
		}
		add(bot.applet);
		bot.applet.setSize(panel.getSize());
		overlay.setVisible(bot.applet != null);
		invalidate();
		repaint();
	}
}