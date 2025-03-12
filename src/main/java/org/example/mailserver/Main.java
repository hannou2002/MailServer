package org.example.mailserver;

import org.example.mailserver.rmi.RMIServer;
import org.example.mailserver.smtp.SMTPServer;
import org.example.mailserver.pop3.POP3Server;

public class Main {
    public static void main(String[] args) {
        // Démarrer le serveur RMI
        new Thread(() -> RMIServer.main(args)).start();

        // Démarrer le serveur SMTP
        new Thread(() -> SMTPServer.main(args)).start();

        // Démarrer le serveur POP3
        new Thread(() -> POP3Server.main(args)).start();
    }
}
