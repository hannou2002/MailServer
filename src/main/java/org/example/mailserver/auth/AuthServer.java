package org.example.mailserver.auth;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthServer {
    public static void main(String[] args) {
        try {
            // Create RMI registry on port 1099
            Registry registry = LocateRegistry.createRegistry(1099);

            // Create and bind the authentication service
            AuthService authService = new AuthServiceImpl();
            registry.rebind("AuthService", authService);

            System.out.println("Authentication Server is running...");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}