package me.winter.trapgame.server;

import me.winter.trapgame.shared.PlayerInfo;
import me.winter.trapgame.shared.TrapGameVersion;
import me.winter.trapgame.shared.packet.Packet;
import me.winter.trapgame.shared.packet.PacketInJoin;
import me.winter.trapgame.shared.packet.PacketInPing;
import me.winter.trapgame.shared.packet.PacketOutKick;
import me.winter.trapgame.shared.packet.PacketOutPong;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * Represents a connection from the server side accepting new clients
 * Is an handler for a ServerSocket
 *
 * Created by winter on 25/03/16.
 */
public class ServerConnection
{
	private TrapGameServer server;

	private DatagramSocket udpSocket;
	private byte[] inputBuffer;
	private boolean acceptNewClients;

	public ServerConnection(TrapGameServer server, int port) throws Exception
	{
		this.server = server;

		inputBuffer = new byte[8 * 1024];
		if(port > 0)
			udpSocket = new DatagramSocket(port);
		else
			udpSocket = new DatagramSocket();

		new Thread(this::acceptInput).start();

		server.getScheduler().addTask(this::lookForAlive, 5000, true);

		acceptNewClients = true;
		server.getLogger().info("The server is listening on " + udpSocket.getLocalPort());
	}

	private void acceptInput()
	{
		String packetName = null;

		while(isOpen()) try
		{
			packetName = null;
			DatagramPacket bufPacket = new DatagramPacket(inputBuffer, inputBuffer.length);

			udpSocket.receive(bufPacket);

			if(!isOpen())
				break;

			Player player = getPlayer(bufPacket.getAddress(), bufPacket.getPort());

			ByteArrayInputStream byteStream = new ByteArrayInputStream(inputBuffer);

			packetName = new DataInputStream(byteStream).readUTF();

			if(packetName.equals("KeepAlive"))
			{
				keepAlive(bufPacket.getAddress(), bufPacket.getPort());
				if(player != null)
					player.getConnection().keepAlive();
				continue;
			}


			Packet packet = (Packet)Class.forName("me.winter.trapgame.shared.packet." + packetName).newInstance();
			packet.readFrom(byteStream);

			//if(server.isDebugMode()) ab00se
			//	System.out.println("Received " + packet.getClass().getSimpleName() + " from " + bufPacket.getAddress().toString() + " port: " + bufPacket.getPort());

			if(player != null)
			{
				player.getConnection().receivePacketLater(packet);
				continue;
			}

			if(packet instanceof PacketInPing)
			{
				sendPacketToGuest(getPong(), bufPacket.getAddress(), bufPacket.getPort());
				continue;
			}

			if(!(packet instanceof PacketInJoin) || !isAcceptingNewClients())
				continue;

			String name = ((PacketInJoin)packet).getPlayerName();

			String invalidReason = getInvalidNameReason(name);
			if(invalidReason != null)
			{
				sendPacketToGuest(new PacketOutKick(invalidReason), bufPacket.getAddress(), bufPacket.getPort());
				continue;
			}

			while(!server.isAvailable(name))
				name += "_";

			int id = server.generateNewPlayerId();

			PlayerInfo info = new PlayerInfo(id, name, server.getColor(id), server.getStatsManager().load(name), 0.5f, 0.5f);

			server.join(new Player(server, info, bufPacket.getAddress(), bufPacket.getPort()));

		}
		catch(SocketException ex)
		{

		}
		catch(ClassNotFoundException ex)
		{
			server.getLogger().log(Level.INFO, "Received packet with invalid name: " + packetName);
		}
		catch(Exception ex)
		{
			server.getLogger().log(Level.WARNING, "Unexpected exception", ex);
		}
	}

	private void lookForAlive()
	{
		for(Player player : new ArrayList<>(server.getPlayers()))
		{
			if(!server.getPlayers().contains(player))
				continue;

			if(System.currentTimeMillis() - player.getConnection().getLastPacketReceived() > 30_000)
				player.timeOut();
		}
	}

	public void keepAlive(InetAddress address, int port)
	{
		new Thread(() -> {
			try
			{
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				new DataOutputStream(byteStream).writeUTF("KeepAlive");

				DatagramPacket data = new DatagramPacket(byteStream.toByteArray(), byteStream.size(), address, port);
				getUdpSocket().send(data);
			}
			catch(Exception ex)
			{
				if(server.isDebugMode())
					server.getLogger().log(Level.WARNING, "An exception occurred while sending data to keep alive a player", ex);
			}
		}).start();
	}

	public void sendPacketToGuest(Packet packet, InetAddress address, int port)
	{
		new Thread(() -> {
			try
			{
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				new DataOutputStream(byteStream).writeUTF(packet.getClass().getSimpleName());
				packet.writeTo(byteStream);

				DatagramPacket data = new DatagramPacket(byteStream.toByteArray(), byteStream.size(), address, port);

				getUdpSocket().send(data);
			}
			catch(Exception ex)
			{
				if(server.isDebugMode())
					server.getLogger().log(Level.WARNING, "An exception occurred while sending data to guest", ex);
			}
		}).start();
	}

	public void sendToAll(Packet packet)
	{
		server.getPlayers().forEach(player -> player.getConnection().sendPacket(packet));
	}

	public void sendToAllLater(Packet packet)
	{
		server.getPlayers().forEach(player -> player.getConnection().sendPacketLater(packet));
	}

	public Player getPlayer(InetAddress address, int port)
	{
		for(Player player : server.getPlayers())
			if(player.getConnection().getAddress().equals(address)
			&& player.getConnection().getPort() == port)
				return player;

		return null;
	}

	/**
	 * Creates a pong packet with infos about the server for client display
	 * @return pong packet
	 */
	public PacketOutPong getPong()
	{
		return new PacketOutPong(TrapGameVersion.GAME_VERSION, server.getName(), server.getPlayers().size(), server.getMaxPlayers());
	}

	public boolean isOpen()
	{
		return !udpSocket.isClosed();
	}

	public synchronized void close()
	{
		getUdpSocket().close();
		notify();
	}

	public DatagramSocket getUdpSocket()
	{
		return udpSocket;
	}

	public boolean isAcceptingNewClients()
	{
		return acceptNewClients;
	}

	public void setAcceptingNewClients(boolean acceptNewClients)
	{
		this.acceptNewClients = acceptNewClients;
	}

	public static String getInvalidNameReason(String name)
	{
		if(name.length() < 3)
			return "Your name should have at least 3 characters.";

		if(name.length() > 20)
			return "Your name can't have more than 20 characters.";

		if(!name.matches("^[A-Za-z0-9_]+$"))
			return "Your name can only have letters, numbers and underscores.";

		return null;
	}
}
