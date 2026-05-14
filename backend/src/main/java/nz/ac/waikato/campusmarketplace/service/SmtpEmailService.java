package nz.ac.waikato.campusmarketplace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailService(JavaMailSender sender,
                            @Value("${app.email.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }
}
