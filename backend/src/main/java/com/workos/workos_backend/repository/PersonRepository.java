package com.workos.workos_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Person;

public interface PersonRepository extends JpaRepository<Person, String> {

    Optional<Person> findByEmail(String email);

    /**
     * The management recipient set for the self-notification rule (spec
     * section 18) — every {@link Person} whose persisted {@code accessRole}
     * is one of the given roles (typically CEO/HR/Operation Manager). See
     * {@link com.workos.workos_backend.service.NotificationService#notify}.
     */
    List<Person> findByAccessRoleIn(Collection<String> accessRoles);

    /**
     * Rows with a non-null {@code accessRole} are exactly the employee
     * accounts created via {@code POST /api/accounts} (spec section 5.2/14)
     * - as opposed to the seeded demo {@link Person} rows used as generic
     * workspace members, which have no login capability and a null
     * {@code accessRole}. Used by the Admin roster (Admin itself is a
     * separate entity/table, never a {@link Person} row, so no further
     * exclusion is needed).
     */
    List<Person> findByAccessRoleIsNotNullOrderByNameAsc();
}
