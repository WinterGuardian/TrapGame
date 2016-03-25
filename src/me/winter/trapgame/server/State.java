package me.winter.trapgame.server;

/**
 * Represents a state of the game server
 *
 * Created by winter on 25/03/16.
 */
public interface State
{
	/**
	 * Called when a player joins the game
	 * @param player
	 */
	void join(Player player);

	/**
	 * Called when a player leaves the game or gets kicked, or lost connection
	 * @param player
	 */
	void leave(Player player);

	/**
	 * Called just after the state has been changed
	 */
	void start();
}
