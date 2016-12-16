package com.company.Practica2FR;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

class ClientServer {

	// GUI elements
	private JPanel panel;
	private JLabel localIPLabel;
	private JTextField serverIPTextField;
	private JTextField serverPortTextField;
	private JLabel serverIPLabel;
	private JLabel serverPortLabel;
	private JButton startConnectionButton;
	private JTextArea serverMessagesTextArea;
	private JButton endConnectionButton;
	private JButton sendButton;
	private JLabel connectionStatus;
	private JTextArea clientMessagesTextArea;
	private JCheckBox debugModeCheckBox;
	private JTextField messageSizeTextField;
	private JLabel messageSize;
	private JCheckBox enterToSend;

	// Server and client variables
	private int port;
	private int messageSizeLimit;
	private String serverIP; // Dirección del servidor al que se va a conectar el cliente

	private DatagramSocket clientSocket;
	private DatagramSocket serverSocket;

	private boolean connectionStarted = false;
	private boolean debugModeEnabled = false;
	private boolean GUImode = true;

	private ClientServer() {

		// Get local IP to display it on a label
		try {
			String myLocalIP = InetAddress.getLocalHost().getHostAddress();
			localIPLabel.setText("IP: " + myLocalIP);
		} catch (UnknownHostException exception) {
			JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			localIPLabel.setText("IP: " + "unknown");
		}

		// Run the server
		startConnectionButton.addActionListener(e -> {

			// If the IP or port fields are empty, show an alert
			if (serverIPTextField.getText().isEmpty() || serverPortTextField.getText().isEmpty()) {
				JOptionPane.showMessageDialog(null, "Fill the empty fields", "Error", JOptionPane.ERROR_MESSAGE);
			} else if (!connectionStarted) {

				// Take the text inside the fields and save it into variables
				messageSizeLimit = Integer.valueOf(messageSizeTextField.getText());
				port = Integer.valueOf(serverPortTextField.getText());
				serverIP = serverIPTextField.getText();

				// Create a new datagramSocket for the server
				try {
					serverSocket = new DatagramSocket(port);
				} catch (SocketException exception) {
					JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}

				connectionStarted = true;

				// Change the text of the connection status label
				connectionStatus.setText("Connected");
				connectionStatus.setForeground(new Color(20, 190, 20)); // Verde oscuro

				receivePackets();
			} else {
				JOptionPane.showMessageDialog(null, "End the current connection before", "Error", JOptionPane.ERROR_MESSAGE);
			}
		});

		// End connection
		endConnectionButton.addActionListener(e -> {

			if (!connectionStarted) {
				JOptionPane.showMessageDialog(null, "Connection didn't start", "Error", JOptionPane.ERROR_MESSAGE);
			} else {

				// Send disconnection message
				String localIP;
				try {
					localIP = InetAddress.getLocalHost().getHostAddress();
				} catch (UnknownHostException exception) {
					JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					localIP = "unknown";
				}
				sendPackets("SERVER: " + localIP + " ended connection");

				// Close client and server
				if (clientSocket != null && !clientSocket.isClosed()) {
					clientSocket.close();
				}

				if (serverSocket != null && !serverSocket.isClosed()) {
					serverSocket.close();
				}

				connectionStarted = false;

				connectionStatus.setText("Disconnected");
				connectionStatus.setForeground(new Color(220, 50, 50)); // Rojo oscuro

				// When the socket is closed, an exception is thrown and captured, then it exits the program; hence there is no need to call "join"

				JOptionPane.showMessageDialog(null, "Connection closed", "Warning", JOptionPane.WARNING_MESSAGE);
			}
		});

		// Send the content of the message text field
		sendButton.addActionListener(e -> {

			String textToSend = clientMessagesTextArea.getText();

			if (!textToSend.isEmpty() && connectionStarted) {
				sendPackets(textToSend);
				clientMessagesTextArea.setText("");
			} else if (!connectionStarted) {
				JOptionPane.showMessageDialog(null,
						"Message wasn't send because connection didn't start", "Error", JOptionPane.ERROR_MESSAGE);
			}
		});

		serverMessagesTextArea.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { setModelText(); }
			public void removeUpdate(DocumentEvent e) { setModelText(); }
			public void changedUpdate(DocumentEvent e) { setModelText(); }

			private void setModelText() {
				SwingUtilities.invokeLater(() -> {
					DefaultCaret caret = (DefaultCaret)serverMessagesTextArea.getCaret();
					caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
				});
			}
		});

		localIPLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				setClipboard(localIPLabel.getText().substring(4));
			}
		});

		serverIPLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				setClipboard(serverIPTextField.getText());
			}
		});

		serverPortLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				setClipboard(serverPortTextField.getText());
			}
		});

		messageSize.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				setClipboard(messageSizeTextField.getText());
			}
		});

		clientMessagesTextArea.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (e.getKeyChar() == '\n' && !clientMessagesTextArea.getText().isEmpty() && enterToSend.isSelected()) {

					int messageLength = clientMessagesTextArea.getText().length();
					String messageWithoutNewLine = clientMessagesTextArea.getText().substring(0, messageLength - 1);
					clientMessagesTextArea.setText(messageWithoutNewLine);
					sendButton.doClick();
					clientMessagesTextArea.setText("");
				}
			}
		});
	}

	private void setClipboard(String str) {
		StringSelection stringSelection = new StringSelection(str);
		Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
		clpbrd.setContents(stringSelection, null);
	}

	// Constructor for the no GUI version
	private ClientServer(String serverIP, int port, int messageSizeLimit, boolean GUImode, boolean connectionStarted) {
		this.serverIP = serverIP;
		this.port = port;
		this.messageSizeLimit = messageSizeLimit;
		this.GUImode = GUImode;
		this.connectionStarted = connectionStarted;
	}

	// Send a message
	private void sendPackets(String textToSend) {

		try {
			InetAddress IPaddress = InetAddress.getByName(this.serverIP);
			clientSocket = new DatagramSocket();

			String input = textToSend;

			if (textToSend.length() > this.messageSizeLimit) {
				input = textToSend.substring(0, this.messageSizeLimit);
			}

			// Update the server message text
			boolean isEmpty = serverMessagesTextArea.getText().isEmpty();
			serverMessagesTextArea.append(isEmpty ? input : ("\n" + input));

			// Create a new packet with the necessary data
			DatagramPacket packet = new DatagramPacket(input.getBytes(), input.getBytes().length, IPaddress, this.port);

			clientSocket.send(packet);

			if (!GUImode && textToSend.equals("\\end")) {
				clientSocket.close();
			}

		} catch (Exception exception) {

			if (!GUImode) {
				System.out.println(exception.getMessage());
			} else {
				JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// Receive the data and update the text field (or console)
	private void receivePackets() {

		new Thread(() -> {

			try {
				// Keep receiving data while the server is running
				while (!serverSocket.isClosed()) {

					if (connectionStarted) {

						String dataOutput;
						String outputText;
						byte[] message = new byte[messageSizeLimit];

						// Create a new packet
						DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

						// Receive and store the data
						serverSocket.receive(receivedPacket);

						// Read the information
						int receivedPacketPort = receivedPacket.getPort();
						InetAddress IP = receivedPacket.getAddress();

						// Convert bytes to a String with UTF-8 coding, also remove the "\0" characters
						dataOutput = new String(receivedPacket.getData(), "UTF-8");
						dataOutput = dataOutput.replaceAll("\0", "");

						if (debugModeCheckBox.isSelected()) {
							outputText = "- " + dataOutput + " (IP: " + IP.getHostAddress() + ", port: " + receivedPacketPort + ")";
						} else {
							outputText = "- " + dataOutput;
						}

						if (!GUImode) {

							// If the word "\end" hasn't been sent yet...
							if (!dataOutput.equals("\\end")) {

								if (debugModeEnabled) {
									System.out.println("- " + dataOutput + " (IP: " + IP.getHostAddress() + ", port: " + receivedPacketPort + ")");
								} else {
									System.out.println("- " + dataOutput);
								}
							} else { // Close all sockets and exit the program
								System.out.println("User " + IP.getHostAddress() + " ended connection");
								clientSocket.close();
								serverSocket.close();
							}
						} else if (dataOutput.equals("\\end")) {
							outputText = "SERVER: " + IP.getHostAddress() + " ended connection";
						}

						// Add new text to the textbox
						boolean isEmpty = serverMessagesTextArea.getText().isEmpty();
						serverMessagesTextArea.append(isEmpty ? outputText : ("\n" + outputText));
					}
				}

				if (!GUImode) {
					System.exit(1);
				}

			} catch (Exception exception) {

				if (!GUImode) {
					System.out.println(exception.getMessage());
				} else {
					JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}

		}).start();
	}

	// Function to use the program without GUI
	private static void noGUI(String[] args) {

		// Default values
		String serverIP = "127.0.0.1";
		int messageSizeLimit = 128; // bytes
		int port = 10000;
		boolean serverAllowed = true;

		// For each argument, check if a certain argument is passed and get the corresponding value
		for (String argument : args) {

			int index = Arrays.asList(args).indexOf(argument);
			switch (argument) {
				case "-ip": serverIP = args[index + 1]; break;
				case "-size": messageSizeLimit = Integer.valueOf(args[index + 1]); break;
				case "-port": port = Integer.valueOf(args[index + 1]); break;
				case "--client": serverAllowed = false; break;
			}
		}

		// Create a new server
		ClientServer clientServerProgram = new ClientServer(serverIP, port, messageSizeLimit, false, true);

		if (Arrays.asList(args).contains("--debug")) {
			clientServerProgram.debugModeEnabled = true;
		}

		// Start client and server sockets
		try { clientServerProgram.clientSocket = new DatagramSocket(); }
		catch (Exception exception) { System.out.println(exception.getMessage()); }

		if (serverAllowed) {

			try { clientServerProgram.serverSocket = new DatagramSocket(port); }
			catch (Exception exception) { System.out.println(exception.getMessage()); }

			if (clientServerProgram.debugModeEnabled) {
				System.out.println("Connected to " + clientServerProgram.serverIP + ", port: " + clientServerProgram.port + ", " +
						"message size limit: " + clientServerProgram.messageSizeLimit + " bytes\n");
			}
			System.out.println("Listening...\n");

			// Create a thread internally to receive packets
			clientServerProgram.receivePackets();
		}

		// Create a thread to send messages while the socket isn't closed
		Thread client = new Thread(() -> {

			Scanner input = new Scanner(System.in);
			System.out.println("Text to send: \n");

			while (!clientServerProgram.clientSocket.isClosed()) {
				clientServerProgram.sendPackets(input.nextLine());
			}
		});

		client.start();

		try {
			client.join();
		} catch (InterruptedException exception) {
			System.out.println(exception.getMessage());
		}
	}

	public static void main(String[] args) {

		if (args.length > 0) {

			if (Arrays.asList(args).contains("--help")) {
				System.out.println("Arguments (no GUI): --noGUI, --client, --debug, -ip, -size, -port");
				System.out.println("Example: --noGUI -ip 127.0.0.1 -port 10000");
			}
			if (Arrays.asList(args).contains("--noGUI")) {
				ClientServer.noGUI(args);
			}
		}
		else {
			JFrame frame = new JFrame("Client-Server Messages");
			frame.setContentPane(new ClientServer().panel);
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setMinimumSize(new Dimension(538, 400));
			frame.setPreferredSize(new Dimension(538, 400));
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		}
	}
}
