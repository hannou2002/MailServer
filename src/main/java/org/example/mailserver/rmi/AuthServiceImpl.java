package org.example.mailserver.rmi;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    protected AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        // Logique d'authentification simple
        return "user1".equals(username) && "password1".equals(password);
    }
}