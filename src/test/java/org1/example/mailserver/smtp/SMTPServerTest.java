package org.example.mailserver.smtp;
import org.example.mailserver.smtp.SMTPServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;

public class SMTPServerTest {

    private static final int PORT = 25;
    private SMTPServer smtpServer;
    private Thread serverThread;

    @BeforeEach
    public void setUp() {
        // Démarrer le serveur SMTP dans un thread séparé
        smtpServer = new SMTPServer();
        serverThread = new Thread(() -> smtpServer.main(new String[]{}));
        serverThread.start();
    }

    @Test
    public void testServerConnection() throws IOException {
        // Connexion au serveur
        try (Socket socket = new Socket("localhost", PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));)
        {
            String response = in.readLine();
            assertEquals("220 Welcome to SMTP Server", response);
        }
    }

    @Test
    public void testSendEmail() throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Étape 1: Connexion et salutation
            String response = in.readLine();
            assertEquals("220 Welcome to SMTP Server", response);

            // Étape 2: Commande HELO
            out.println("HELO client.example.com");
            response = in.readLine();
            assertEquals("250 Hello", response);

            // Étape 3: Commande MAIL FROM
            out.println("MAIL FROM:<user1@example.com>");
            response = in.readLine();
            assertEquals("250 OK", response);

            // Étape 4: Commande RCPT TO
            out.println("RCPT TO:<user2@example.com>");
            response = in.readLine();
            assertEquals("250 OK", response);

            // Étape 5: Commande DATA
            out.println("DATA");
            response = in.readLine();
            assertEquals("354 Start mail input; end with <CRLF>.<CRLF>", response);

            // Étape 6: Envoi du contenu de l'email
            out.println("From: user1@example.com");
            out.println("To: user2@example.com");
            out.println("Subject: Test email");
            out.println("");
            out.println("This is a test email.");
            out.println(".");

            // Étape 7: Vérification de la réponse finale
            response = in.readLine();
            assertEquals("250 OK", response);

            // Étape 8: Déconnexion
            out.println("QUIT");
            response = in.readLine();
            assertEquals("221 Bye", response);
        }
    }

    @Test
    public void testInvalidCommand() throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Étape 1: Connexion et salutation
            String response = in.readLine();
            assertEquals("220 Welcome to SMTP Server", response);

            // Étape 2: Commande invalide
            out.println("INVALID_COMMAND");
            response = in.readLine();
            assertEquals("503 Bad sequence of commands", response);
        }
    }

    @Test
    public void testEmailWithLargeContent() throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Étape 1: Connexion et salutation
            String response = in.readLine();
            assertEquals("220 Welcome to SMTP Server", response);

            // Étape 2: Commande HELO
            out.println("HELO client.example.com");
            response = in.readLine();
            assertEquals("250 Hello", response);

            // Étape 3: Commande MAIL FROM
            out.println("MAIL FROM:<user1@example.com>");
            response = in.readLine();
            assertEquals("250 OK", response);

            // Étape 4: Commande RCPT TO
            out.println("RCPT TO:<user2@example.com>");
            response = in.readLine();
            assertEquals("250 OK", response);

            // Étape 5: Commande DATA
            out.println("DATA");
            response = in.readLine();
            assertEquals("354 Start mail input; end with <CRLF>.<CRLF>", response);

            // Étape 6: Envoi d'un email volumineux (dépassant 10 Mo)
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 10 * 1024 * 1024; i++) { // 10 Mo de données
                largeContent.append("a");
            }
            out.println(largeContent.toString());
            out.println(".");

            // Étape 7: Vérification de la réponse finale
            response = in.readLine();
            assertEquals("552 Message size exceeds fixed maximum", response);
        }
    }
}