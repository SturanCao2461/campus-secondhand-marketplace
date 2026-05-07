package nz.ac.waikato.campusmarketplace.service;

public interface EmailService {
    void send(String to, String subject, String body);
}
