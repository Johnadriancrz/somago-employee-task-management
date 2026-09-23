package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.dto.PersonResponse;
import com.workos.workos_backend.service.PersonService;

/** Implements the read-only People endpoint from BACKEND.md. No auth required, matching the documented exception. */
@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping
    public List<PersonResponse> list() {
        return personService.listPeople().stream().map(PersonResponse::from).toList();
    }
}
