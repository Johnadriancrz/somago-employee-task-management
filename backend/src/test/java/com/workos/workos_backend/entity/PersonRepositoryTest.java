package com.workos.workos_backend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;

import com.workos.workos_backend.repository.PersonRepository;

/**
 * Verifies the Person entity maps correctly and the migration's unique
 * email constraint is enforced. Runs against an isolated in-memory H2
 * database (src/test/resources/application.properties) via the same
 * Flyway migration used against real MySQL, never against workos_db.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PersonRepositoryTest {

    @Autowired
    private PersonRepository personRepository;

    @Test
    void savesAndReloadsAllFields() {
        Person saved = personRepository.save(new Person(
                "sarah-chen", "Sarah Chen", "sarah.chen@workos.dev", "SC", "Senior PM",
                "bg-secondary-container text-on-secondary-container"));

        Person reloaded = personRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Sarah Chen");
        assertThat(reloaded.getEmail()).isEqualTo("sarah.chen@workos.dev");
        assertThat(reloaded.getInitials()).isEqualTo("SC");
        assertThat(reloaded.getRole()).isEqualTo("Senior PM");
        assertThat(reloaded.getChipClass()).isEqualTo("bg-secondary-container text-on-secondary-container");
    }

    @Test
    void accessRoleAndPasswordHashDefaultToNull() {
        Person saved = personRepository.save(new Person(
                "jordan-lee", "Jordan Lee", "jordan.lee@workos.dev", "JL", "Ops",
                "chip"));

        Person reloaded = personRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAccessRole()).isNull();
        assertThat(reloaded.getPasswordHash()).isNull();
    }

    @Test
    void savesAndReloadsAccessRoleAndPasswordHash() {
        Person person = new Person(
                "casey-ceo", "Casey CEO", "casey.ceo@workos.dev", "CC", "Ops", "chip");
        person.setAccessRole("CEO");
        person.setPasswordHash("$2a$10$hashedvalue");
        Person saved = personRepository.save(person);

        Person reloaded = personRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAccessRole()).isEqualTo("CEO");
        assertThat(reloaded.getPasswordHash()).isEqualTo("$2a$10$hashedvalue");
    }

    @Test
    void rejectsDuplicateEmail() {
        personRepository.saveAndFlush(new Person("alex-morgan", "Alex Morgan", "dup@workos.dev", "AM", "Lead Architect", "chip"));

        assertThatThrownBy(() -> personRepository.saveAndFlush(
                new Person("priya-patel", "Priya Patel", "dup@workos.dev", "PP", "Product Ops", "chip")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
