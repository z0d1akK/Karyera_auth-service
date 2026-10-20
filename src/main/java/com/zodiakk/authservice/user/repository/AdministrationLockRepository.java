package com.zodiakk.authservice.user.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdministrationLockRepository {

    private final EntityManager entityManager;

    public void lock() {
        entityManager.createNativeQuery("SELECT id FROM administration_lock WHERE id = 1 FOR UPDATE").getSingleResult();
    }
}
