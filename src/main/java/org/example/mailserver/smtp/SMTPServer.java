package org.example.mailserver.smtp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
//RCPT TO:
public class SMTPServer {
    private static final int PORT = 25;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                // Créer un nouveau thread pour gérer la connexion
                new SMTPHandler(socket).start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}