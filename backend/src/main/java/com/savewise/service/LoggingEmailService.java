package com.savewise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Development stand-in: prints the email to the application log instead of sending it.
 * For production, add spring-boot-starter-mail and provide a JavaMailSender-based EmailService
 * (remove @Service from this class so there is only one implementation).
 */
@Service
public class LoggingEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("\n--- EMAIL (dev log only) ---\nTo: {}\nSubject: {}\n\n{}\n----------------------------", to, subject, body);
    }
}
