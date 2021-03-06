package me.winter.trapgame.client.board;

import me.winter.trapgame.shared.BoardFiller;
import me.winter.trapgame.shared.PlayerInfo;
import me.winter.trapgame.shared.packet.PacketInClick;
import me.winter.trapgame.shared.packet.PacketInCursorMove;
import me.winter.trapgame.util.ColorTransformer;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Represents the panel where players clicks and contains the buttons
 *
 * Created by Alexander Winter on 2016-03-28.
 */


public class PlayBoard extends JPanel implements MouseMotionListener, MouseListener, KeyListener
{
	private TrapGameBoard container;

	private Map<Point, PlayerInfo> scores;
	private int boardWidth, boardHeight;
	private boolean boardLocked, spectator, mouseIn;

	private long lastFreeze; //last time the player missed

	private Map<Color, BufferedImage> preloaded;

	private boolean specialSounds = false;
	private Random rand = new Random();

	private List<FailAnimation> fails;

	public PlayBoard(TrapGameBoard container, int width, int height)
	{
		this.container = container;
		this.scores = new HashMap<>();
		preloaded = new HashMap<>();
		fails = new ArrayList<>();
		mouseIn = false;
		boardLocked = true;
		lastFreeze = 0;

		addMouseMotionListener(this);
		addMouseListener(this);
		addKeyListener(this);

		setFocusable(true);

		setBackground(new Color(0, 0, 0, 0));

		boolean windows;

		try
		{
			windows = System.getProperty("os.name").toLowerCase().contains("win");
		}
		catch(Exception ex)
		{
			windows = false;
		}

		BufferedImage image = getCursorImage(container.getClient().getColor(), !windows);
		setCursor(Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(image.getWidth() / 2, image.getHeight() / 2), "TrapGame"));

		prepare(width, height);
	}

	public void prepare(int boardWidth, int boardHeight)
	{
		setBoardWidth(boardWidth);
		setBoardHeight(boardHeight);
		removeAll();
		scores.clear();
		setLayout(new GridLayout(boardWidth, boardHeight, 0, 0));
	}

	@Override
	public void paintComponent(Graphics graphics)
	{
		Graphics2D g2draw = (Graphics2D) graphics;

		g2draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2draw.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		g2draw.drawImage(container.getContainer().getResourceManager().getImage("background"), -getX(), 0, container.getWidth(), container.getHeight(), null);
		//g2draw.drawImage(container.getContainer().getResourceManager().getImage("board"), 0, 0, getWidth(), getHeight(), null);

		float buttonWidth = getWidth() / (float)getBoardWidth();
		float buttonHeight = getHeight() / (float)getBoardHeight();

		for(int x = 0; x < getBoardWidth(); x++)
		{
			for(int y = 0; y < getBoardHeight(); y++)
			{
				PlayerInfo player = scores.get(new Point(x, y));

				int xCeil = (int)((x + 1) * buttonWidth) > (int)(x * buttonWidth) + (int)buttonWidth ? 1 : 0;
				int yCeil = (int)((y + 1) * buttonHeight) > (int)(y * buttonHeight) + (int)buttonHeight ? 1 : 0;

				int width = (int)buttonWidth + xCeil;
				int height = (int)buttonHeight + yCeil;

				if(player != null)
				{
					g2draw.setColor(new ColorTransformer(player.getColor(), 200));
					g2draw.fillRoundRect(
							(int)(x * buttonWidth) + (int)(3 * buttonWidth / 256),
							(int)(y * buttonHeight) + (int)(3 * buttonHeight / 256),
							width - (int)(3 * buttonWidth / 128),
							height - (int)(3 * buttonHeight / 128),
							width / 6, height / 6);
				}
				else if(!isSpectator() && !isBoardLocked() && lastFreeze - System.nanoTime() < 0 && mouseIn && isHover(new Point(x, y)))
				{
					boolean aroundOwn = false;
					boolean atLeastOne = false;

					for(Point point : scores.keySet())
					{
						if(scores.get(point) == container.getClient())
						{
							atLeastOne = true;

							if(point.getX() + 1 == x && point.getY() == y
							|| point.getX() - 1 == x && point.getY() == y
							|| point.getX() == x && point.getY() + 1 == y
							|| point.getX() == x && point.getY() - 1 == y)
							{
								aroundOwn = true;
								break;
							}
						}
					}

					if(!atLeastOne || aroundOwn)
					{
						g2draw.setColor(new ColorTransformer(container.getClient().getColor(), 100));
						g2draw.fillRoundRect(
								(int)(x * buttonWidth) + (int)(3 * buttonWidth / 256),
								(int)(y * buttonHeight) + (int)(3 * buttonHeight / 256),
								width - (int)(3 * buttonWidth / 128),
								height - (int)(3 * buttonHeight / 128),
								width / 6, height / 6);
					}
				}

				g2draw.drawImage(container.getContainer().getResourceManager().getImage("game-button"), (int)(x * buttonWidth), (int)(y * buttonHeight), (int)buttonWidth + xCeil, (int)buttonHeight + yCeil, null);

			}
		}

		for(PlayerInfo player : container.getPlayers())
		{
			if(player == container.getClient())
				continue;
			g2draw.drawImage(getCursorImage(player.getColor(), true),
					(int)(player.getCursorX() * getWidth()) - 16,
					(int)(player.getCursorY() * getHeight()) - 16, null);
		}

		for(int i = 0; i < fails.size(); i++)
		{
			if(fails.get(i).finished())
			{
				fails.remove(i);
				continue;
			}

			int x = fails.get(i).getLocation().x;
			int y = fails.get(i).getLocation().y;

			g2draw.drawImage(container.getContainer().getResourceManager().getImage("fail-icon"), (int)(x - buttonWidth / 2), (int)(y - buttonHeight / 2), (int)buttonWidth, (int)buttonHeight, null);
		}
	}

	public BufferedImage getCursorImage(Color color, boolean transparency)
	{
		BufferedImage baseCursor = (BufferedImage)container.getContainer().getResourceManager().getImage("cursor");

		if(!transparency)
		{
			BufferedImage image = new BufferedImage(baseCursor.getWidth(), baseCursor.getHeight(), BufferedImage.TYPE_INT_ARGB);

			Graphics2D graphics2D = image.createGraphics();

			graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics2D.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			graphics2D.setColor(Color.white);
			graphics2D.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics2D.drawImage(getCursorImage(color, true), 0, 0, null);

			for(int x = 0; x < baseCursor.getWidth(); x++)
			{
				for(int y = 0; y < baseCursor.getHeight(); y++)
				{
					Color currentColor = new Color(image.getRGB(x, y));

					if(currentColor.equals(Color.white))
						image.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
				}
			}
			graphics2D.dispose();

			return image;
		}

		if(preloaded.containsKey(color))
			return preloaded.get(color);

		BufferedImage image = new BufferedImage(baseCursor.getWidth(), baseCursor.getHeight(), BufferedImage.TYPE_INT_ARGB);

		Graphics2D graphics2D = image.createGraphics();

		for(int x = 0; x < baseCursor.getWidth(); x++)
		{
			for(int y = 0; y < baseCursor.getHeight(); y++)
			{
				Color currentColor = new Color(baseCursor.getRGB(x, y));
				graphics2D.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), currentColor.getRed()));
				graphics2D.drawRect(x, y, 1, 1);
			}
		}


		graphics2D.dispose();


		preloaded.put(color, image);
		return image;
	}

	public boolean isHover(Point point)
	{
		return new Point((int)(container.getClient().getCursorX() * getBoardWidth()), (int)(container.getClient().getCursorY() * getBoardHeight())).equals(point);
	}

	public int getScore(PlayerInfo info)
	{
		int score = 0;

		for(Point point : scores.keySet())
			if(scores.get(point) == info)
				score++;

		return score;
	}

	public void place(int playerId, Point point)
	{
		PlayerInfo player = container.getPlayer(playerId);

		if(player == null && playerId > 0)
			return;

		if(point.getX() < 0 || point.getY() < 0
				|| point.getX() >= getBoardWidth()
				|| point.getY() >= getBoardHeight())
			return;

		if(player == null)
			scores.remove(point);
		else
			scores.put(point, player);

		SwingUtilities.invokeLater(() -> {
			revalidate();
			repaint();
			container.getScoreboard().build();
		});
	}

	public void fill(int playerId, Point point)
	{
		if(container.getPlayer(playerId) == null)
			return;

		BoardFiller.tryFill(point, container.getPlayer(playerId), scores, getBoardWidth(), getBoardHeight());
		revalidate();
		repaint();

		SwingUtilities.invokeLater(() -> container.getScoreboard().build());
	}

	private void click(Point point)
	{
		if(isBoardLocked())
			return;

		this.requestFocusInWindow();

		if(lastFreeze - System.nanoTime() > 0)
		{
			fail();
			return;
		}

		if(getScores().containsKey(point))
		{
			lastFreeze = System.nanoTime() + 500_000_000;
			fail();
			return;
		}

		if(scores.values().contains(container.getClient())
		&& scores.get(new Point(point.x + 1, point.y)) != container.getClient()
		&& scores.get(new Point(point.x - 1, point.y)) != container.getClient()
		&& scores.get(new Point(point.x, point.y + 1)) != container.getClient()
		&& scores.get(new Point(point.x, point.y - 1)) != container.getClient())
		{
			lastFreeze = System.nanoTime() + 500_000_000;
			fail();
			return;
		}

		container.getContainer().getConnection().sendPacketLater(new PacketInClick(point));
		scores.put(point, container.getClient());
		playClickSound();

		SwingUtilities.invokeLater(() -> {
			revalidate();
			repaint();
			container.getScoreboard().build();
		});
	}

	private void fail()
	{
		FailAnimation anim = new FailAnimation(new Point((int)(container.getClient().getCursorX() * getWidth()) + rand.nextInt(10), (int)(container.getClient().getCursorY() * getHeight()) + rand.nextInt(10)), System.nanoTime());
		fails.add(anim);
		container.getContainer().getScheduler().addTask(() -> {
			if(this.isShowing())
			{
				revalidate();
				repaint();
			}
		}, anim.getLength(), false);
		playFailSound();
	}

	@Override
	public void mouseDragged(MouseEvent e)
	{
		mouseMoved(e);
	}

	@Override
	public void mouseMoved(MouseEvent e)
	{
		container.getClient().setCursor((float)e.getX() / getWidth(), (float)e.getY() / getHeight());
		container.getContainer().getConnection().sendPacketLater(new PacketInCursorMove(container.getClient().getCursorX(), container.getClient().getCursorY()));
		revalidate();
		repaint();
	}

	@Override
	public void mouseClicked(MouseEvent e)
	{

	}

	@Override
	public void mousePressed(MouseEvent e)
	{
		click(new Point(e.getX() * getBoardWidth() / getWidth(), e.getY() * getBoardHeight() / getHeight()));
	}

	@Override
	public void mouseReleased(MouseEvent e)
	{

	}

	@Override
	public void mouseEntered(MouseEvent e)
	{
		mouseIn = true;
		revalidate();
		repaint();
	}

	@Override
	public void mouseExited(MouseEvent e)
	{
		mouseIn = false;
		revalidate();
		repaint();
	}

	@Override
	public void keyTyped(KeyEvent event)
	{

	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if(event.getKeyCode() == KeyEvent.VK_SPACE
		|| event.getKeyCode() == KeyEvent.VK_W
		|| event.getKeyCode() == KeyEvent.VK_E)
		{
			click(new Point((int)(container.getClient().getCursorX() * getBoardWidth()), (int)(container.getClient().getCursorY() * getBoardHeight())));
		}
		else if(event.getKeyCode() == KeyEvent.VK_F8)
		{
			specialSounds = !specialSounds;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{

	}

	public void playClickSound()
	{
		container.getContainer().getResourceManager().getSound((specialSounds ? "special-" : "") + "click" + rand.nextInt(3)).play();
	}

	public void playFailSound()
	{
		container.getContainer().getResourceManager().getSound((specialSounds ? "special-" : "") + "fail").play();
	}

	public Map<Point, PlayerInfo> getScores()
	{
		return scores;
	}

	public int getBoardWidth()
	{
		return boardWidth;
	}

	public void setBoardWidth(int boardWidth)
	{
		this.boardWidth = boardWidth;
	}

	public int getBoardHeight()
	{
		return boardHeight;
	}

	public void setBoardHeight(int boardHeight)
	{
		this.boardHeight = boardHeight;
	}

	public boolean isBoardLocked()
	{
		return boardLocked;
	}

	public void setBoardLocked(boolean boardLocked)
	{
		this.boardLocked = boardLocked;
	}

	public boolean isSpectator()
	{
		return spectator;
	}

	public void setSpectator(boolean spectator)
	{
		this.spectator = spectator;
	}
}