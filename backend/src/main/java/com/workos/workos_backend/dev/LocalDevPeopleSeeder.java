package com.workos.workos_backend.dev;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * LOCAL DEVELOPMENT ONLY. Seeds the {@code people} table with the same
 * accounts the frontend's own mock data uses
 * (workos-app/src/lib/data.ts#PEOPLE), so the Workspace/Board APIs have real
 * Person rows to reference when exercised manually against MySQL.
 *
 * <p>Only registered under the {@code local-dev} Spring profile — never
 * runs in any other environment. Idempotent: each record is inserted only
 * if a Person with that id doesn't already exist; existing rows (including
 * ones a developer has since edited) are never overwritten, and re-running
 * (e.g. app restart) never creates duplicates.
 *
 * <p>After seeding, verifies that the configured local-dev actor
 * ({@code app.local-dev.actor-person-id}, see {@link ActingPersonResolver})
 * actually exists — if it doesn't, startup fails loudly with a clear
 * message rather than letting every ownership/membership check fail later
 * against a non-existent actor.
 */
@Component
@Profile("local-dev")
public class LocalDevPeopleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDevPeopleSeeder.class);

    /** Mirrors workos-app/src/lib/data.ts#PEOPLE exactly (id, name, email, initials, role, chipClass). */
    private static final List<Person> SEED_PEOPLE = List.of(
            new Person("sarah-chen", "Sarah Chen", "sarah.chen@workos.dev", "SC", "Senior PM",
                    "bg-secondary-container text-on-secondary-container"),
            new Person("alex-morgan", "Alex Morgan", "alex.morgan@workos.dev", "AM", "Lead Architect",
                    "bg-primary-fixed text-on-primary-fixed"),
            new Person("priya-patel", "Priya Patel", "priya.patel@workos.dev", "PP", "Product Ops",
                    "bg-surface-container-high text-primary"),
            new Person("david-kim", "David Kim", "david.kim@workos.dev", "DK", "Fullstack Dev",
                    "bg-surface-container-highest text-secondary"),
            new Person("maya-reyes", "Maya Reyes", "maya.reyes@workos.dev", "MR", "Legal & Program Ops",
                    "bg-primary/10 text-primary"),
            new Person("noah-ibrahim", "Noah Ibrahim", "noah.ibrahim@workos.dev", "NI", "Security & QA",
                    "bg-tertiary-container/15 text-tertiary"));

    private final PersonRepository personRepository;
    private final ActingPersonResolver actingPersonResolver;

    public LocalDevPeopleSeeder(PersonRepository personRepository, ActingPersonResolver actingPersonResolver) {
        this.personRepository = personRepository;
        this.actingPersonResolver = actingPersonResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        int inserted = seedMissingPeople();
        log.info("[local-dev] People seed: inserted {} new row(s), {} already present.",
                inserted, SEED_PEOPLE.size() - inserted);

        String actorId = actingPersonResolver.currentPersonId();
        if (!personRepository.existsById(actorId)) {
            List<String> knownIds = SEED_PEOPLE.stream().map(Person::getId).toList();
            throw new IllegalStateException(
                    "app.local-dev.actor-person-id is set to '" + actorId + "', which does not exist as a "
                            + "Person. Set it to one of the seeded ids " + knownIds
                            + ", or an id you've created yourself.");
        }
        log.info("[local-dev] Acting person for this run: {}", actorId);
    }

    /**
     * Inserts any of the six fixed demo Person rows that don't already
     * exist; never overwrites an existing row, even one a developer has
     * since edited (see the class Javadoc). Also called by {@link
     * com.workos.workos_backend.service.ResetService} to restore People as
     * part of {@code POST /api/reset}, so "reset" and "startup seed" share
     * one definition of what People demo state means.
     *
     * @return the number of rows inserted
     */
    public int seedMissingPeople() {
        int inserted = 0;
        for (Person person : SEED_PEOPLE) {
            if (personRepository.existsById(person.getId())) {
                continue;
            }
            personRepository.save(person);
            inserted++;
        }
        return inserted;
    }
}
