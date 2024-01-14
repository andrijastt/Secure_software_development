package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.config.SecurityUtil;
import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;

@Controller

public class PersonsController {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    public PersonsController(PersonRepository personRepository, UserRepository userRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/persons/{id}")
    @PreAuthorize("hasAuthority('VIEW_PERSON')")
    public String person(@PathVariable int id, Model model, HttpSession httpSession) {
        String csrf = httpSession.getAttribute("CSRF_TOKEN").toString();
        model.addAttribute("CSRF_TOKEN", csrf);
        model.addAttribute("persons", personRepository.getAll());
        model.addAttribute("person", personRepository.get("" + id));
        return "person";
    }

    @GetMapping("/myprofile")
    public String self(Model model, Authentication authentication, HttpSession httpSession) {
        User user = (User) authentication.getPrincipal();
        String csrf = httpSession.getAttribute("CSRF_TOKEN").toString();
        model.addAttribute("CSRF_TOKEN", csrf);
        model.addAttribute("person", personRepository.get("" + user.getId()));
        return "person";
    }

    @DeleteMapping("/persons/{id}")
    public ResponseEntity<Void> person(@PathVariable int id) {
        personRepository.delete(id);
        userRepository.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-person")
//    @PreAuthorize("hasAuthority('UPDATE_PERSON')")
    public String updatePerson(Person person, HttpSession httpSession, @RequestParam("csrfToken") String csrfToken) throws AccessDeniedException {

        User currentUser = SecurityUtil.getCurrentUser();
        boolean hasPermission = SecurityUtil.hasPermission("UPDATE_PERSON");

        if(!hasPermission){
            int userId = currentUser.getId();
            int id = Integer.parseInt(person.getId());
            if(userId != id){
                LOG.error("User with id: " + userId + " tried to change user with id: " + id + " data");
                throw new AccessDeniedException("Can't change data!");
            }
        }

        String sessionToken = httpSession.getAttribute("CSRF_TOKEN").toString();
        if(!csrfToken.equals(sessionToken)){
            LOG.error("CSRF_TOKEN doesn't match");
            throw new AccessDeniedException("Access forbidden!");
        }
        personRepository.update(person);

        boolean canViewPersonList = SecurityUtil.hasPermission("VIEW_PERSONS_LIST");
        if(canViewPersonList)
            return "redirect:/persons/" + person.getId();
        else
            return "redirect:/myprofile";
    }

    @GetMapping("/persons")
    @PreAuthorize("hasAuthority('VIEW_PERSONS_LIST')")
    public String persons(Model model, HttpSession httpSession) {
        String csrf = httpSession.getAttribute("CSRF_TOKEN").toString();
        model.addAttribute("CSRF_TOKEN", csrf);
        model.addAttribute("persons", personRepository.getAll());
        return "persons";
    }

    @GetMapping(value = "/persons/search", produces = "application/json")
    @ResponseBody
    public List<Person> searchPersons(@RequestParam String searchTerm) throws SQLException {
        return personRepository.search(searchTerm);
    }
}
