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

public class ClientServer {

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

		// Consigue la IP local para mostrarla en una etiqueta
		try {
			String myLocalIP = InetAddress.getLocalHost().getHostAddress();
			localIPLabel.setText("IP: " + myLocalIP);
		} catch (UnknownHostException exception) {
			JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			localIPLabel.setText("IP: " + "unknown");
		}

		// Lanza el servidor al hacer click en el botón de "start connection"
		startConnectionButton.addActionListener(e -> {

			// Si los campos de IP o puerto están vacíos muestra alerta
			if (serverIPTextField.getText().isEmpty() || serverPortTextField.getText().isEmpty()) {
				JOptionPane.showMessageDialog(null, "Fill the empty fields", "Error", JOptionPane.ERROR_MESSAGE);
			} else if (!connectionStarted) {

				// Recoge los valores de los campos e introdúcelos en variables
				messageSizeLimit = Integer.valueOf(messageSizeTextField.getText());
				port = Integer.valueOf(serverPortTextField.getText());
				serverIP = serverIPTextField.getText();

				// Crea una nuevo datagramSocket para el servidor
				try {
					serverSocket = new DatagramSocket(port);
				} catch (SocketException exception) {
					JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}

				connectionStarted = true;

				// Cambia la etiqueta del estado de conexión
				connectionStatus.setText("Connected");
				connectionStatus.setForeground(new Color(20, 190, 20)); // Verde oscuro

				// Llama a la función principal para recibir los packets
				receivedPackets();
			} else {
				JOptionPane.showMessageDialog(null, "End the current connection before", "Error", JOptionPane.ERROR_MESSAGE);
			}
		});

		// Termina la conexión al hacer click en el botón "End Connection"
		endConnectionButton.addActionListener(e -> {

			if (!connectionStarted) {
				JOptionPane.showMessageDialog(null, "Connection didn't start", "Error", JOptionPane.ERROR_MESSAGE);
			} else {

				// Envía mensaje de dexconexión
				String localIP;
				try {
					localIP = InetAddress.getLocalHost().getHostAddress();
				} catch (UnknownHostException exception) {
					JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					localIP = "unknown";
				}
				sendPackets("SERVER: " + localIP + " ended connection");

				// Cierra cliente y servidor
				if (clientSocket != null && !clientSocket.isClosed()) {
					clientSocket.close();
				}

				if (serverSocket != null && !serverSocket.isClosed()) {
					serverSocket.close();
				}

				connectionStarted = false;

				connectionStatus.setText("Disconnected");
				connectionStatus.setForeground(new Color(220, 50, 50)); // Rojo oscuro

				// Al cerrar el socket crea una excepción (que es capturada), y luego sale del programa
				// Por ello no hace falta hacer ningún join

				JOptionPane.showMessageDialog(null, "Connection closed", "Warning", JOptionPane.WARNING_MESSAGE);
			}
		});

		// Envía el mensaje que está en el campo de texto
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

	// Constructor para la versión sin interfaz gráfica
	private ClientServer(String serverIP, int port, int messageSizeLimit, boolean GUImode, boolean connectionStarted) {
		this.serverIP = serverIP;
		this.port = port;
		this.messageSizeLimit = messageSizeLimit;
		this.GUImode = GUImode;
		this.connectionStarted = connectionStarted;
	}

	// Envía un mensaje
	private void sendPackets(String textToSend) {

		try {
			InetAddress IPaddress = InetAddress.getByName(this.serverIP);
			clientSocket = new DatagramSocket();

			String input = textToSend;

			if (textToSend.length() > this.messageSizeLimit) {
				input = textToSend.substring(0, this.messageSizeLimit);
			}

			// Actualiza el área de texto con lo que el cliente haya escrito
			boolean isEmpty = serverMessagesTextArea.getText().isEmpty();
			serverMessagesTextArea.append(isEmpty ? input : ("\n" + input));

			// Crea paquete y con datos los datos necesarios
			DatagramPacket packet = new DatagramPacket(input.getBytes(), input.getBytes().length, IPaddress, this.port);

			// Envía el paquete a traves del socket
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

	// Recibe datos y actualiza el campo de texto (o la consola)
	private void receivedPackets() {

		new Thread(() -> {

			try {
				// Contínuamente recibe datos mientras no esté el socket cerrado
				while (!serverSocket.isClosed()) {

					if (connectionStarted) {

						String dataOutput;
						String outputText;
						byte[] message = new byte[messageSizeLimit];

						// Crea recursos para atender una nueva conexión (crea un nuevo paquete)
						DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

						// Escucha del socket y cuando recibo datos los almacena
						serverSocket.receive(receivedPacket);

						// Lee información desde el paquete
						int receivedPacketPort = receivedPacket.getPort();
						InetAddress IP = receivedPacket.getAddress();

						// Pasa de bytes a String con codificación UTF-8 y quita los caracteres "\0" que haya
						dataOutput = new String(receivedPacket.getData(), "UTF-8");
						dataOutput = dataOutput.replaceAll("\0", "");

						// Si la casilla de modo debug (depuración) está activada, muestra más datos de la conexión
						if (debugModeCheckBox.isSelected()) {
							outputText = "- " + dataOutput + " (IP: " + IP.getHostAddress() + ", port: " + receivedPacketPort + ")";
						} else {
							outputText = "- " + dataOutput;
						}

						// Si está en modo sin interfaz
						if (!GUImode) {

							// Si no se ha enviado la palabra clave "\end" para terminar conexión
							if (!dataOutput.equals("\\end")) {

								if (debugModeEnabled) {
									System.out.println("- " + dataOutput + " (IP: " + IP.getHostAddress() + ", port: " + receivedPacketPort + ")");
								} else {
									System.out.println("- " + dataOutput);
								}
							} else { // Cierra todas las conexiones y sale del programa
								System.out.println("User " + IP.getHostAddress() + " ended connection");
								clientSocket.close();
								serverSocket.close();
							}
						} else if (dataOutput.equals("\\end")) {
							outputText = "SERVER: " + IP.getHostAddress() + " ended connection";
						}

						// Añade texto nuevo a la caja de texto
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

	// Función para usar el programa sin interfaz gráfica
	private static void noGUI(String[] args) {

		// Valores por defecto
		String serverIP = "127.0.0.1";
		int messageSizeLimit = 128; // bytes
		int port = 10000;
		boolean serverAllowed = true;

		// Para cada argumento comprueba si se pasa un dato y actualiza los valores por defecto
		for (String argument : args) {

			int index = Arrays.asList(args).indexOf(argument);
			switch (argument) {
				case "-ip": serverIP = args[index + 1]; break;
				case "-size": messageSizeLimit = Integer.valueOf(args[index + 1]); break;
				case "-port": port = Integer.valueOf(args[index + 1]); break;
				case "--client": serverAllowed = false; break;
			}
		}

		// Crea un cliente servidor con los datos apropiados
		ClientServer clientServerProgram = new ClientServer(serverIP, port, messageSizeLimit, false, true);

		if (Arrays.asList(args).contains("--debug")) {
			clientServerProgram.debugModeEnabled = true;
		}

		// Se inicializan los sockets de cliente y servidor
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

			// Crea internamente un hilo para recibir mensajes (Servidor)
			clientServerProgram.receivedPackets();
		}

		// Crea un hilo para enviar mensajes (Cliente) mientras que el socket esté abierto
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

			// Muestra ayuda sobre la ejecución del programa por consola
			if (Arrays.asList(args).contains("--help")) {
				System.out.println("Arguments (no GUI): --noGUI, --client, --debug, -ip, -size, -port");
				System.out.println("Example: --noGUI -ip 127.0.0.1 -port 10000");
			}

			// Si como argumento se le pasa "--noGUI" se ejecuta el programa sin interfaz
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
