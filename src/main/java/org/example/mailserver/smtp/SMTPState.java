package org.example.mailserver.smtp;

public enum SMTPState {
    INIT, HELO, MAIL_FROM, RCPT_TO, DATA, QUIT
}