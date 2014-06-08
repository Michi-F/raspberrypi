package com.my.pccontrol.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

public class host
{
	// port
	public static final int port = 12801;
	// public constants
	public static final String REQ_POWER_ON = "REQ_POWER_ON";
	public static final String REQ_POWER_OFF = "REQ_POWER_OFF";
	public static final String REQ_RESET = "REQ_RESET";
	public static final String RESPONSE_POWER_ON_PROCESSED = "REQ_POWER_ON_PROCESSED";
	public static final String RESPONSE_POWER_OFF_PROCESSED = "REQ_POWER_OFF_PROCESSED";
	public static final String RESPONSE_RESET_PROCESSED = "REQ_RESET_PROCESSED";
	public static final String RESPONSE_REQUEST_REFUSED = "RESPONSE_REQUEST_REFUSED";
	public static final String RESPONSE_UNKNOWN_REQUEST = "RESPONSE_UNKNOWN_REQUEST";

	// max. time that a request can block service in miliseconds
	// should be more than max. HIGH_TIME_X + delay for pin level change to low
	private static final int MAX_REQUEST_BLOCK_TIME = 8000;
	// time for high level in miliseconds
	private static final int HIGH_TIME_POWER_ON = 100;
	private static final int HIGH_TIME_POWER_OFF = 3000;
	private static final int HIGH_TIME_RESET = 100;

	// log
	private final Logger log = Logger.getLogger(host.class.getName());

	// state of the service: waiting for an request to be finished or free
	private boolean isBlocked = false;
	private Date blockStarted = null;
	private HostThread blockThread = null;

	private final GpioController gpio;
	private final GpioPinDigitalOutput pinPower;
	private final GpioPinDigitalOutput pinReset;

	// Singleton
	private static host instance = null;

	public static void main(String args[])
	{
		host.getInstance();
	}

	// Singleton
	public static host getInstance()
	{
		if (host.instance == null)
		{
			host.instance = new host();
			return host.instance;
		}
		else
		{
			return host.instance;
		}
	}

	private host()
	{
		this.gpio = GpioFactory.getInstance();
		this.pinPower = this.gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "PC Power Pin", PinState.LOW);
		this.pinReset = this.gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "PC Reset Pin", PinState.LOW);

		try
		{
			Thread.sleep(5000);
		}
		catch (InterruptedException e1)
		{
			System.out.println("Error: could not wait until PINs are initialized. Abort service. Error: " +
					e1.getMessage());
			return;
		}

		try
		{
			Handler handler = new FileHandler("PCPowerOnHost.log", true);
			SimpleFormatter formatter = new SimpleFormatter();
			handler.setFormatter(formatter);
			this.log.addHandler(handler);
		}
		catch (SecurityException | IOException e)
		{
			System.out.println("Log-Handler could not be initialized. Abort service. Error: " + e.getMessage());
			return;
		}

		this.log.log(Level.INFO, "Starting Host Service...");

		try (ServerSocket serverSocket = new ServerSocket(host.port))
		{
			this.log.log(Level.INFO, "Server started on Port: " + serverSocket.getLocalPort());
			while (true)
			{
				try
				{
					Socket socket = serverSocket.accept();
					this.log.log(Level.INFO, "new socket from " + socket.getInetAddress() + ":" + socket.getPort());

					// check if a request blocks service for more than
					// MAX_REQUEST_BLOCK_TIME seconds
					long compareTime = (new Date().getTime() - (MAX_REQUEST_BLOCK_TIME));
					Date compareDate = new Date(compareTime);
					if (this.blockStarted != null && this.blockStarted.before(compareDate))
					{
						this.releaseBlock(); // release Block
					}

					if (!this.isBlocked) // if host is free for processing
					{
						this.blockThread = null;
						this.isBlocked = true;
						this.blockStarted = new Date();

						this.blockThread = new HostThread(socket, true);
						this.blockThread.start();
					}
					else
					{
						HostThread newThread = new HostThread(socket, false);
						newThread.start();
					}
				}
				catch (IOException ex)
				{
					this.log.log(Level.SEVERE, null, ex);
				}
			}
		}
		catch (IOException ex)
		{
			System.out.println(ex.toString());
		}
	}

	private void releaseBlock()
	{
		this.blockThread = null;
		this.isBlocked = false;
		this.blockStarted = null;
	}

	private void releaseBlock(HostThread t)
	{
		if (t == this.blockThread)
		{
			this.releaseBlock();
		}
	}

	private class HostThread extends Thread
	{
		private final Socket hostThreadSocket;
		private boolean process = false;

		HostThread(Socket socket, boolean process)
		{
			this.hostThreadSocket = socket;
			this.process = process;
		}

		@Override
		public void run()
		{
			boolean processSuccess = false;
			try
			{
				if (this.process)
				{
					processSuccess = this.processRequest();
				}
				else
				{
					this.refuseRequest();
				}
			}
			catch (IOException e)
			{
				host.this.log.log(Level.SEVERE, "error while running HostThread", e);
			}

			try
			{
				this.hostThreadSocket.close();
			}
			catch (IOException e)
			{
				host.this.log.log(Level.SEVERE, "error: could not close HostThread ", e);
			}

			if (processSuccess)
			{
				host.this.releaseBlock(this);
			}
		}

		private boolean processRequest() throws IOException
		{
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
					this.hostThreadSocket.getInputStream()));
			String request = bufferedReader.readLine();

			if (request.equals(REQ_POWER_ON))
			{
				host.this.log.log(Level.INFO, "Power On request from " + this.hostThreadSocket.getInetAddress() + ":" +
						this.hostThreadSocket.getPort());
				this.pcPowerOn();
				this.sendMessage(RESPONSE_POWER_ON_PROCESSED);
				return true;
			}
			else if (request.equals(REQ_POWER_OFF))
			{
				host.this.log.log(Level.INFO, "Power Off request from " + this.hostThreadSocket.getInetAddress() + ":" +
						this.hostThreadSocket.getPort());
				this.pcPowerOff();
				this.sendMessage(RESPONSE_POWER_OFF_PROCESSED);
				return true;
			}
			else if (request.equals(REQ_RESET))
			{
				host.this.log.log(Level.INFO, "Reset request from " + this.hostThreadSocket.getInetAddress() + ":" +
						this.hostThreadSocket.getPort());
				this.pcReset();
				this.sendMessage(RESPONSE_RESET_PROCESSED);
				return true;
			}
			else
			{
				host.this.log.log(Level.INFO, "Unknown request from " + this.hostThreadSocket.getInetAddress() + ":" +
						this.hostThreadSocket.getPort());
				this.sendMessage(RESPONSE_UNKNOWN_REQUEST);
				return false;
			}
		}

		private void refuseRequest() throws IOException
		{
			this.sendMessage(host.RESPONSE_REQUEST_REFUSED);
		}

		private void sendMessage(String m) throws IOException
		{
			OutputStream outputStream = this.hostThreadSocket.getOutputStream();
			PrintStream printStream = new PrintStream(outputStream);
			printStream.print(m);

			host.this.log.log(Level.INFO, "Send Message: '" + m + "' to " + this.hostThreadSocket.getInetAddress() +
					":" + this.hostThreadSocket.getPort());
		}

		private void pcPowerOn()
		{
			host.this.pinPower.high();
			try
			{
				Thread.sleep(host.HIGH_TIME_POWER_ON);
			}
			catch (InterruptedException e)
			{

			}
			host.this.pinPower.low();
		}

		private void pcPowerOff()
		{
			host.this.pinPower.high();
			try
			{
				Thread.sleep(host.HIGH_TIME_POWER_OFF);
			}
			catch (InterruptedException e)
			{

			}
			host.this.pinPower.low();
		}

		private void pcReset()
		{
			host.this.pinReset.high();
			try
			{
				Thread.sleep(host.HIGH_TIME_RESET);
			}
			catch (InterruptedException e)
			{

			}
			host.this.pinReset.low();
		}
	}
}