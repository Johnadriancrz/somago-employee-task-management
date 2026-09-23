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
}
