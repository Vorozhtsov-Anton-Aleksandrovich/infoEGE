package com.github.anton.infoege.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    Optional<Invite> findByTokenHash(String tokenHash);

    /** С блокировкой строки: две одновременные регистрации по одной ссылке не пройдут обе. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :tokenHash")
    Optional<Invite> findForUpdate(String tokenHash);

    @Query("select i from Invite i where i.userId is null and i.usedAt is null order by i.createdAt desc")
    List<Invite> findPendingNewStudents();

    @Modifying
    @Query("delete from Invite i where i.userId = :userId and i.usedAt is null")
    void deleteUnusedForUser(Long userId);
}
