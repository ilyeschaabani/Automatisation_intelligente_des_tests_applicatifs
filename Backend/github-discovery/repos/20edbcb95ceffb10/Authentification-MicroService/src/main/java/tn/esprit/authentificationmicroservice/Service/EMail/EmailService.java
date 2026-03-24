package tn.esprit.authentificationmicroservice.Service.EMail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import tn.esprit.authentificationmicroservice.dto.Mailbody;

@Service
public class EmailService {

    private  final JavaMailSender mailSender;

    public  EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSimpleMessage(Mailbody mailbody){

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(mailbody.to());
        message.setFrom("Ilyeschaabani857@gmail.com");
        message.setSubject(mailbody.subject());
        message.setText(mailbody.text());

        mailSender.send(message);
    }
}
