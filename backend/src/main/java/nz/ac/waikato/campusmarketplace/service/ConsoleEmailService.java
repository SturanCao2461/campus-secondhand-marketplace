package nz.ac.waikato.campusmarketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void send(String to, String subject, String body) {
        System.out.println("===== EMAIL (console mode) =====");
        System.out.println("To:      " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Body:");
        System.out.println(body);
        System.out.println("================================");
        log.info("[email/console] to={} subject={}", to, subject);
    }
}
