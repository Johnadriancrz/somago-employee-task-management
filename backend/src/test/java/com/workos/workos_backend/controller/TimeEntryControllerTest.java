package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.TimeEntryService;

/**
 * HTTP-level tests for the Time clock endpoints, verifying status codes and
 * the exact JSON contract from BACKEND.md. Runs as the fixed local-dev actor
 * (sarah-chen); a second person's entries are set up directly through the
 * service (same approach as TaskControllerTest) since the local-dev actor is
 * fixed for the whole Spring context. Role-scoped {@code /entries} scenarios
 * (spec section 9) mutate sarah-chen's own accessRole directly via
 * PersonRepository, since the fixed local-dev actor can't otherwise be
 * swapped for a differently-privileged one mid-test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class TimeEntryControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private PersonRepository personRepository;

    private void grantSarahChenAccessRole(String accessRole) {
        Person sarahChen = personRepository.findById("sarah-chen").orElseThrow();
        sarahChen.setAccessRole(accessRole);
        personRepository.save(sarahChen);
    }

    @Test
    void statusReturnsNullWhenNotClockedIn() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/time/status").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString().trim()).isEqualTo("null");
    }

    @Test
    void clockInReturns201WithDocumentedShapeAndServerDerivedActor() {
        MvcTestResult result = mvc.post().uri("/api/time/clock-in").exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.personId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.clockIn").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.clockOut").isNull();
    }

    @Test
    void clockInTwiceReturns409() {
        mvc.post().uri("/api/time/clock-in").exchange();

        MvcTestResult result = mvc.post().uri("/api/time/clock-in").exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void statusReflectsTheOpenEntryAfterClockingIn() {
        mvc.post().uri("/api/time/clock-in").exchange();

        MvcTestResult result = mvc.get().uri("/api/time/status").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.personId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.clockOut").isNull();
    }

    @Test
    void clockOutClosesTheOpenEntry() {
        mvc.post().uri("/api/time/clock-in").exchange();

        MvcTestResult result = mvc.post().uri("/api/time/clock-out").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.clockOut").isNotNull();
    }

    @Test
    void clockOutWithoutClockingInReturns409() {
        MvcTestResult result = mvc.post().uri("/api/time/clock-out").exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void entriesForCeoWithNoFilterReturnsEveryPersonsEntries() {
        // sarah-chen clocks in as a regular actor, *then* is promoted to CEO —
        // CEO is exempt from clock-in itself (see clockInReturns403ForCeo), but
        // still retains all-employees visibility over records that already exist.
        timeEntryService.clockIn("alex-morgan");
        mvc.post().uri("/api/time/clock-in").exchange();
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.get().uri("/api/time/entries").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
    }

    @Test
    void entriesForCeoWithPersonIdFilterScopesToThatPerson() {
        timeEntryService.clockIn("alex-morgan");
        mvc.post().uri("/api/time/clock-in").exchange();
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.get().uri("/api/time/entries?personId=sarah-chen").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].personId").isEqualTo("sarah-chen");
    }

    @Test
    void clockInReturns403ForCeo() {
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.post().uri("/api/time/clock-in").exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void entriesForNonPrivilegedActorOnlyReturnsOwnEntriesEvenWithNoFilter() {
        timeEntryService.clockIn("alex-morgan");
        mvc.post().uri("/api/time/clock-in").exchange();

        MvcTestResult result = mvc.get().uri("/api/time/entries").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].personId").isEqualTo("sarah-chen");
    }

    @Test
    void entriesForNonPrivilegedActorIgnoresPersonIdFilterForSomeoneElse() {
        timeEntryService.clockIn("alex-morgan");
        mvc.post().uri("/api/time/clock-in").exchange();

        MvcTestResult result = mvc.get().uri("/api/time/entries?personId=alex-morgan").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].personId").isEqualTo("sarah-chen");
    }
}
