package me.winter.trapgame.server.command;

import me.winter.trapgame.server.Player;

import java.util.Arrays;
import java.util.List;

/**
 * A command to change the board size
 *
 * Created by Alexander Winter on 2016-03-28.
 */
public class BoardSizeCommand implements Command
{
	@Override
	public String getName()
	{
		return "boardsize";
	}

	@Override
	public List<String> getAliases()
	{
		return Arrays.asList("setboardsize", "changeboardsize");
	}

	@Override
	public String getDescription()
	{
		return "Changes the board size to the specified values";
	}

	@Override
	public String getUsage()
	{
		return "/boardsize width height";
	}

	@Override
	public void execute(Player player, String label, String[] arguments)
	{
		if(arguments.length != 2)
		{
			player.sendMessage("Invalid usage: " + getUsage());
			return;
		}

		try
		{
			int width = Integer.parseInt(arguments[0]);
			int height = Integer.parseInt(arguments[1]);

			player.getServer().setBoardSize(width, height);
		}
		catch(NumberFormatException ex)
		{
			player.sendMessage("Invalid argument(s), width and height should be integers.");
		}
	}
}