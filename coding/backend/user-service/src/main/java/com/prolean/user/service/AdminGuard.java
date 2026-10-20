package com.prolean.user.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes last-active-admin checks with a transaction-scoped PostgreSQL advisory lock.
 *
 * <p>Two concurrent requests that each demote or deactivate a different admin must not both observe
 * "two admins remain" and leave the system with zero. The lock is held until the surrounding
 * transaction commits.
 */
@Component
public class AdminGuard {

    /** Arbitrary application-wide key; the ASCII value of "ADMIN". */
    private static final long ADMIN_LOCK_KEY = 0x41444D494EL;

    private final EntityManager entityManager;

    public AdminGuard(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAdminChanges() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", ADMIN_LOCK_KEY)
                .getSingleResult();
    }
}
