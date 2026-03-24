package tn.esprit.microservice.formationms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/formations")
public class Controller {

    @Autowired
    private FormationService formationService;

    @PostMapping("/add")
    public Formation addFormation(@RequestBody Formation formation) {
        return formationService.addFormation(formation);
    }

    @PutMapping("/update/{id}")
    public Formation updateFormation(@PathVariable String id, @RequestBody Formation formation) {
        return formationService.updateFormation(id, formation);
    }

    @DeleteMapping("/delete/{id}")
    public String deleteFormation(@PathVariable String id) {
        return formationService.deleteFormation(id);
    }

    @GetMapping("/all")
    public List<Formation> getAllFormations() {
        return formationService.getAllFormations();
    }

    @GetMapping("/get/{id}")
    public Formation getFormationById(@PathVariable String id) {
        return formationService.getFormationById(id);
    }

}
