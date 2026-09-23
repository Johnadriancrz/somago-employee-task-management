package com.workos.workos_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Person;

public interface PersonRepository extends JpaRepository<Person, String> {

    Optional<Person> findByEmail(String email);
}
