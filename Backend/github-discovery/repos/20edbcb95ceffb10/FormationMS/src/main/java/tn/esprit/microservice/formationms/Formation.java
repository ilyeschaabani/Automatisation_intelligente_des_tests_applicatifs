package tn.esprit.microservice.formationms;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)

public class Formation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long idFormation;
    String image;
    String titreFormation;
    String description;
    Date dateDebut;
    Date dateFin;
    CategoryResource categorie;
    double rating;
    double prix;

      /*
    @OneToOne
    Evaluation evaluation;
    @ManyToMany(cascade = CascadeType.ALL)
    Set<User> users;

    @ManyToOne
    Panier panier;
*/
}