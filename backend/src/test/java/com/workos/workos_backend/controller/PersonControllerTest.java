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

import com.workos.workos_backend.service.AccountService;

/**
 * HTTP-level test for the read-only People endpoint. Runs under local-dev
 * so the seeded people (workos-app/src/lib/data.ts#PEOPLE) are present.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class PersonControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AccountService accountService;

    @Test
    void listReturnsSeededPeopleWithDocumentedShape() {
        MvcTestResult result = mvc.get().uri("/api/people").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(6);
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].name").asArray()
                .containsExactly("Sarah Chen");
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].email").asArray()
                .containsExactly("sarah.chen@workos.dev");
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].initials").asArray()
                .containsExactly("SC");
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].role").asArray()
                .containsExactly("Senior PM");
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].chipClass").asArray()
                .containsExactly("bg-secondary-container text-on-secondary-container");
    }

    @Test
    void responseNeverIncludesPasswordHash() throws java.io.UnsupportedEncodingException {
        MvcTestResult result = mvc.get().uri("/api/people").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    @Test
    void accessRoleIsNullForSeededDemoPeople() {
        MvcTestResult result = mvc.get().uri("/api/people").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')].accessRole").asArray()
                .containsExactly((Object) null);
    }

    @Test
    void chatDirectoryExcludesSeededDemoPeopleWithNoAccessRole() {
        MvcTestResult result = mvc.get().uri("/api/people/chat-directory").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='alex-morgan')]").asArray().isEmpty();
    }

    @Test
    void chatDirectoryDoesNotChangeTheDocumentedPeopleEndpoint() {
        // GET /api/people (Board/task/workspace member pickers) must still
        // return every seeded Person row, chat-directory filtering or not.
        MvcTestResult result = mvc.get().uri("/api/people").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(6);
    }

    @Test
    void chatDirectoryIncludesARealEmployeeAccount() {
        accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "IT");

        MvcTestResult result = mvc.get().uri("/api/people/chat-directory").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.email=='new.hire@workos.dev')].accessRole").asArray()
                .containsExactly("IT");
    }

    @Test
    void employeeDirectoryExcludesSeededDemoPeopleWithNoAccessRole() {
        MvcTestResult result = mvc.get().uri("/api/people/employee-directory").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='sarah-chen')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='alex-morgan')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='david-kim')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='maya-reyes')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='noah-ibrahim')]").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='priya-patel')]").asArray().isEmpty();
    }

    @Test
    void employeeDirectoryDoesNotChangeTheDocumentedPeopleEndpoint() {
        // GET /api/people (Board/task/workspace member pickers) must still
        // return every seeded Person row, employee-directory filtering or not.
        MvcTestResult result = mvc.get().uri("/api/people").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(6);
    }

    @Test
    void employeeDirectoryIncludesExistingRealEmployeeAccounts() {
        accountService.createAccount("Jaq Castro", "jaq.castro@workos.dev", "Password123!", "CEO");
        accountService.createAccount("John Adrian Cruz", "john.cruz@workos.dev", "Password123!", "IT");

        MvcTestResult result = mvc.get().uri("/api/people/employee-directory").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.email=='jaq.castro@workos.dev')].accessRole").asArray()
                .containsExactly("CEO");
        assertThat(result).bodyJson().extractingPath("$[?(@.email=='john.cruz@workos.dev')].accessRole").asArray()
                .containsExactly("IT");
    }

    @Test
    void employeeDirectoryIncludesANewlyCreatedEmployeeAccount() {
        accountService.createAccount("New Hire", "new.hire@workos.dev", "Password123!", "Marketing");

        MvcTestResult result = mvc.get().uri("/api/people/employee-directory").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.email=='new.hire@workos.dev')].accessRole").asArray()
                .containsExactly("Marketing");
    }

    @Test
    void employeeDirectoryMatchesTheAdminAccountsRosterPopulation() {
        accountService.createAccount("Jaq Castro", "jaq.castro@workos.dev", "Password123!", "CEO");
        accountService.createAccount("John Adrian Cruz", "john.cruz@workos.dev", "Password123!", "IT");

        MvcTestResult directoryResult = mvc.get().uri("/api/people/employee-directory").exchange();
        assertThat(directoryResult).hasStatusOk();
        assertThat(directoryResult).bodyJson().extractingPath("$.length()")
                .isEqualTo(accountService.listAccounts().size());
    }
}
