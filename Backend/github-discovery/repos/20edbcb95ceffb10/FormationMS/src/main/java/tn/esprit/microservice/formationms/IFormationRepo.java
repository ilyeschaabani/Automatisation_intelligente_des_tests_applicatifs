package tn.esprit.microservice.formationms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IFormationRepo  extends JpaRepository<Formation , String> {

}
