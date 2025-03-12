package org.example.mailserver.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthService extends Remote {
    boolean authenticate(String username, String password) throws RemoteException;
}