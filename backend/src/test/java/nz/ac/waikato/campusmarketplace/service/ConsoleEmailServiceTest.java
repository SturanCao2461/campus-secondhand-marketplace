package nz.ac.waikato.campusmarketplace.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleEmailServiceTest {

    @Test
    void writesSubjectAndBodyToStdout() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf));
        try {
            new ConsoleEmailService().send("alice@students.waikato.ac.nz",
                    "Reset your password", "Click http://x/reset?token=abc");
        } finally {
            System.setOut(original);
        }
        String out = buf.toString();
        assertTrue(out.contains("alice@students.waikato.ac.nz"));
        assertTrue(out.contains("Reset your password"));
        assertTrue(out.contains("http://x/reset?token=abc"));
    }
}
