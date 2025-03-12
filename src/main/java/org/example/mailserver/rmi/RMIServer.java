package org.example.mailserver.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    public static void main(String[] args) {
        try {
            AuthService authService = new AuthServiceImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("AuthService", authService);
            System.out.println("AuthService is ready.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}