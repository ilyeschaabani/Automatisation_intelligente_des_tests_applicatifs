package tn.esprit.authentificationmicroservice.dto;

import lombok.Builder;

@Builder
public record Mailbody(String to , String subject, String text) {


}
