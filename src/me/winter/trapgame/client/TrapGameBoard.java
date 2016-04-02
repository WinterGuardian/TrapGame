package me.winter.trapgame.client;

import me.winter.trapgame.shared.BoardFiller;
import me.winter.trapgame.shared.PlayerInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the board used to play the game
 * TrapGameClient should switch to this content pane after being connected to a server
 *
 * Created by Alexander Winter on 2016-03-27.
 */
public class TrapGameBoard extends JPanel
{
	private TrapGameClient container;

	private PlayBoard playBoard;
	private Chat chat;

	private Map<Point, PlayerInfo> boardContent;
	private List<PlayerInfo> players;
	private int playerId;

	private int boardWidth, boardHeight;
	private boolean boardLocked, spectator;

	private Image buttonFrame;

	public TrapGameBoard(TrapGameClient container)
	{
		this.container = container;

		try
		{
			buttonFrame = ImageIO.read(ClassLoader.class.getResourceAsStream("/frame.png"));
		}
		catch(IOException ex)
		{
			System.err.println("Failed to load logo image");
			ex.printStackTrace(System.err);
		}

		chat = new Chat(this);

		JPanel chatContainer = new JPanel();
		chatContainer.setLayout(new BorderLayout());

		JButton disconnect = new JButton("Disconnect");
		disconnect.addMouseListener(new MouseListener()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{

			}

			@Override
			public void mousePressed(MouseEvent e)
			{

			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				getContainer().getConnection().close();
				dispose();
				getContainer().goToMenu();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{

			}

			@Override
			public void mouseExited(MouseEvent e)
			{

			}
		});
		chatContainer.add(disconnect, BorderLayout.NORTH);
		chatContainer.add(chat, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(chat);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		chatContainer.add(scroll, BorderLayout.EAST);
		chatContainer.add(new ChatInput(this), BorderLayout.SOUTH);



		playBoard = new PlayBoard(this);
/*
		setLayout(new GridBagLayout());
		GridBagConstraints rules = new GridBagConstraints();

		rules.fill = GridBagConstraints.BOTH;

		rules.gridx = 0;
		rules.gridy = 0;
		rules.gridwidth = 1;
		rules.gridheight = 4;

		add(new JPanel(), rules);

		rules.gridx += rules.gridwidth;
		rules.gridwidth = 4;
		rules.weightx = 1;
		rules.weighty = 1;

		add(playBoard, rules);

		rules.gridx += rules.gridwidth;
		rules.gridwidth = 1;
		rules.weightx = 0;
		rules.weighty = 0;

		add(chatContainer, rules);*/

		setLayout(new BorderLayout());

		add(playBoard, BorderLayout.CENTER);
		add(chatContainer, BorderLayout.EAST);

		dispose();
	}

	public void init(int playerId, List<PlayerInfo> players)
	{
		this.playerId = playerId;
		this.players = players;

		boardContent = new HashMap<>();
		playBoard.setCursor(getClient().getColor());
		revalidate();
		repaint();
	}

	public void setBoardSize(int boardWidth, int boardHeight)
	{
		this.boardWidth = boardWidth;
		this.boardHeight = boardHeight;

		playBoard.removeAll();
		playBoard.setLayout(new GridLayout(boardWidth, boardHeight, 0, 0));

		for(int i = 0; i < boardWidth; i++)
		{
			for(int j = 0; j < boardHeight; j++)
			{
				playBoard.add(new TrapButton(this, new Point(i, j)));
			}
		}
		revalidate();
		repaint();
	}

	public void start()
	{
		reset();
		setBoardLocked(false);
	}

	public void stop()
	{
		setBoardLocked(true);
		spectator = false;
	}

	public void reset()
	{
		boardContent.clear();
		boardLocked = true;
		for(Component component : playBoard.getComponents())
			if(component instanceof TrapButton)
				component.setBackground(null);

		revalidate();
		repaint();
	}

	public void dispose()
	{
		boardContent = null;
		players = null;
		playerId = -1;
		boardWidth = -1;
		boardHeight = -1;
		boardLocked = true;
		playBoard.removeAll();
		chat.reset();
	}

	public PlayerInfo getClient()
	{
		return getPlayer(playerId);
	}

	public PlayerInfo getPlayer(int playerId)
	{
		for(PlayerInfo player : players)
			if(player.getPlayerId() == playerId)
				return player;
		return null;
	}

	public boolean inGame()
	{
		return players != null && boardContent != null && playerId >= 0;
	}

	public void join(PlayerInfo info)
	{
		if(!inGame())
			throw new IllegalStateException("Game board not initialized");
		players.add(info);
	}

	public void leave(int playerId)
	{
		if(this.playerId == playerId)
			return;

		players.removeIf(player -> player.getPlayerId() == playerId);
	}

	public boolean canClick(Point point)
	{
		return !boardContent.containsKey(point);
	}

	public void place(int playerId, Point point)
	{
		if(boardLocked && !spectator)
			return;

		PlayerInfo player = getPlayer(playerId);

		if(point.getX() < 0 || point.getY() < 0
				|| point.getX() >= getBoardWidth()
				|| point.getY() >= getBoardHeight()
				|| boardContent.containsKey(point))
			return;


		boardContent.put(point, player);

		for(Component component : playBoard.getComponents())
			if(component instanceof TrapButton && ((TrapButton)component).getPoint().equals(point))
				component.setBackground(player.getColor());
	}

	public void fill(int playerId, Point point)
	{
		BoardFiller.tryFill(point, getPlayer(playerId), boardContent, getBoardWidth(), getBoardHeight());

		for(Component component : playBoard.getComponents())
			if(component instanceof TrapButton)
			{
				PlayerInfo owner = boardContent.get(((TrapButton)component).getPoint());
				if(owner != null)
					component.setBackground(owner.getColor());
			}
	}

	public TrapGameClient getContainer()
	{
		return container;
	}

	public List<PlayerInfo> getPlayers()
	{
		return players;
	}

	public PlayBoard getPlayBoard()
	{
		return playBoard;
	}

	public boolean isBoardLocked()
	{
		return boardLocked;
	}

	public void setBoardLocked(boolean locked)
	{
		boardLocked = locked;
	}

	public int getBoardWidth()
	{
		return boardWidth;
	}

	public int getBoardHeight()
	{
		return boardHeight;
	}

	public Chat getChat()
	{
		return chat;
	}

	public boolean isSpectator()
	{
		return spectator;
	}

	public void setSpectator(boolean spectator)
	{
		this.spectator = spectator;
	}

	public Image getButtonFrame()
	{
		return buttonFrame;
	}
}
