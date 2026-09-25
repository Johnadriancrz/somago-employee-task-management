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

    /**
     * Chat's DM directory: only {@link Person} rows with a real WorkOS login
     * account (non-null {@code accessRole}), so Chat never lets someone start
     * a DM with a seeded/demo workspace-member Person who has no account and
     * therefore can't read it. Same {@code accessRole IS NOT NULL} filter as
     * the Admin roster (see {@link
     * com.workos.workos_backend.service.AccountService#listAccounts()}),
     * reusing {@link PersonRepository#findByAccessRoleIsNotNullOrderByNameAsc()}.
     * Scoped to this method only - {@link #listPeople()} (Board/task/workspace
     * member pickers) is unchanged and still returns every Person row.
     */
    @Transactional(readOnly = true)
    public List<Person> listChatDirectory() {
        return personRepository.findByAccessRoleIsNotNullOrderByNameAsc();
    }

    /**
     * Time Clock's employee list: the same real-employee-account population
     * as the Admin roster ({@link
     * com.workos.workos_backend.service.AccountService#listAccounts()}) and
     * Chat's directory ({@link #listChatDirectory()}) - only {@link Person}
     * rows with a non-null {@code accessRole}, so seeded/demo Person rows
     * never show up as clockable employees. Reuses the same repository
     * query as those two, kept as its own method (rather than calling
     * {@link #listChatDirectory()}) to match this class's existing
     * one-method-per-consumer convention. Scoped to this method only -
     * {@link #listPeople()} is unchanged.
     */
    @Transactional(readOnly = true)
    public List<Person> listEmployeeDirectory() {
        return personRepository.findByAccessRoleIsNotNullOrderByNameAsc();
    }
}
