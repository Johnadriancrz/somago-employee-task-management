package com.workos.workos_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;

/** Read-only: GET /api/people (BACKEND.md) — nothing creates/edits people in this phase. */
@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<Person> listPeople() {
        return personRepository.findAll();
    }
}
