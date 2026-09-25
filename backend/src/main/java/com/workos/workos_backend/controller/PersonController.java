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

    /**
     * Chat-specific: only Person rows with a real WorkOS login account
     * (non-null accessRole), so Chat's DM directory never offers a seeded
     * demo Person who can't log in to read the conversation. Scoped to this
     * endpoint only - {@code GET /api/people} itself is unchanged for
     * Board/task/workspace-member consumers that need every Person row. No
     * auth required, same as {@code GET /api/people}.
     */
    @GetMapping("/chat-directory")
    public List<PersonResponse> listChatDirectory() {
        return personService.listChatDirectory().stream().map(PersonResponse::from).toList();
    }

    /**
     * Time Clock-specific: only Person rows with a real employee account
     * (non-null accessRole) - the same population the Admin "Employee
     * accounts" page shows, so Time Clock never lists a seeded/demo Person
     * who has no login. Scoped to this endpoint only - {@code GET
     * /api/people} itself is unchanged. No auth required, same as {@code GET
     * /api/people}/{@code /chat-directory} - Time Clock is used by every
     * employee, not just CEO/HR/Admin.
     */
    @GetMapping("/employee-directory")
    public List<PersonResponse> listEmployeeDirectory() {
        return personService.listEmployeeDirectory().stream().map(PersonResponse::from).toList();
    }
}
