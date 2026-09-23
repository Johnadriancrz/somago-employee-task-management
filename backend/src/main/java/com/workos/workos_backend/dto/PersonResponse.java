package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.Person;

/** Matches the frontend's {@code Person} type exactly (workos-app/src/lib/types.ts). */
public record PersonResponse(
        String id,
        String name,
        String email,
        String initials,
        String role,
        String chipClass) {

    public static PersonResponse from(Person person) {
        return new PersonResponse(
                person.getId(),
                person.getName(),
                person.getEmail(),
                person.getInitials(),
                person.getRole(),
                person.getChipClass());
    }
}
