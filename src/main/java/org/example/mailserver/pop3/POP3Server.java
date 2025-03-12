package org.example.mailserver.pop3;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class POP3Server {
    private static final int PORT = 110;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                // Créer un nouveau thread pour gérer la connexion
                new POP3Handler(socket).start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}