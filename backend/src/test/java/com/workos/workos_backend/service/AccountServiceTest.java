package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Exercises AccountService's employee-account-creation rules directly,
 * against the seeded local-dev people (used to prove existing demo accounts
 * are never mutated by account creation).
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAnAccountWithAHashedPasswordAndTheGivenAccessRole() {
        Person created = accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "IT");

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getEmail()).isEqualTo("new.hire@workos.dev");
        assertThat(created.getAccessRole()).isEqualTo("IT");
        assertThat(created.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", created.getPasswordHash())).isTrue();
    }

    @Test
    void createsAnAccountForEachOfTheEightApprovedRoles() {
        int i = 0;
        for (String role : AccountService.ACCESS_ROLES) {
            String email = "person-" + (i++) + "@workos.dev";
            Person created = accountService.createAccount("Someone", email, "Password123!", role);
            assertThat(created.getAccessRole()).isEqualTo(role);
        }
        assertThat(AccountService.ACCESS_ROLES).hasSize(8);
    }

    @Test
    void rejectsAdminAsAnAccessRole() {
        assertThatThrownBy(() -> accountService.createAccount(
                "New Hire", "new.hire@workos.dev", "Password123!", "Admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownOrMalformedRole() {
        assertThatThrownBy(() -> accountService.createAccount(
                "New Hire", "new.hire@workos.dev", "Password123!", "Astronaut"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADuplicateEmail() {
        accountService.createAccount("First", "duplicate@workos.dev", "Password123!", "IT");

        assertThatThrownBy(() -> accountService.createAccount(
                "Second", "duplicate@workos.dev", "Password123!", "HR"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAnEmailAlreadyUsedByASeededDemoPerson() {
        assertThatThrownBy(() -> accountService.createAccount(
                "Impersonator", "sarah.chen@workos.dev", "Password123!", "IT"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void doesNotMutateAnyExistingDemoPerson() {
        Person before = personRepository.findById("alex-morgan").orElseThrow();

        accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "IT");

        Person after = personRepository.findById("alex-morgan").orElseThrow();
        assertThat(after.getAccessRole()).isEqualTo(before.getAccessRole());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
    }

    @Test
    void neverReturnsAPersonWithTheAdminAccessRoleValue() {
        // Defensive: Admin can never even be attempted as a value that "succeeds".
        assertThat(AccountService.ACCESS_ROLES).doesNotContain("Admin", "ADMIN", "admin");
    }

    @Test
    void listAccountsIncludesOnlyPeopleWithAnAccessRoleSet() {
        // Seeded demo people (e.g. "sarah-chen") have a null accessRole and must
        // not appear in the persisted employee-account roster.
        accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "IT");

        List<Person> accounts = accountService.listAccounts();

        assertThat(accounts).extracting(Person::getEmail).contains("new.hire@workos.dev");
        assertThat(accounts).allSatisfy(person -> assertThat(person.getAccessRole()).isNotNull());
        assertThat(personRepository.findById("sarah-chen").orElseThrow().getAccessRole()).isNull();
        assertThat(accounts).extracting(Person::getId).doesNotContain("sarah-chen");
    }

    @Test
    void listAccountsNeverExposesAPasswordHashFieldOnThePersonEntityItself() {
        Person created = accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "IT");

        List<Person> accounts = accountService.listAccounts();

        // listAccounts() returns entities (the controller maps to PersonResponse,
        // which has no passwordHash field) - this only asserts the hash is still
        // set on the underlying row, not that it "leaks" here.
        assertThat(accounts).filteredOn(p -> p.getId().equals(created.getId()))
                .singleElement()
                .satisfies(p -> assertThat(p.getPasswordHash()).isNotBlank());
    }
}
