package com.github.anton.infoege.invite;

import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class InviteService {

    public static final Duration LIFETIME = Duration.ofDays(7);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InviteService(InviteRepository inviteRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Ошибка регистрации, которую можно показать ученику. */
    public static class InviteException extends RuntimeException {
        public InviteException(String message) {
            super(message);
        }
    }

    /** Приглашение нового ученика. Возвращает токен: он показывается один раз и нигде не хранится. */
    @Transactional
    public String inviteNewStudent(String displayName, Long teacherId) {
        return issue(displayName.trim(), null, teacherId);
    }

    /** Новая ссылка взамен неиспользованного приглашения. Старая перестаёт работать. */
    @Transactional
    public String reissuePending(Long inviteId, Long teacherId) {
        Invite old = pending(inviteId);
        inviteRepository.delete(old);
        return issue(old.getStudentDisplayName(), null, teacherId);
    }

    @Transactional
    public void deletePending(Long inviteId) {
        inviteRepository.delete(pending(inviteId));
    }

    /** Ссылка для сброса пароля существующего ученика. Предыдущие неиспользованные ссылки удаляются. */
    @Transactional
    public String issuePasswordReset(Long studentId, Long teacherId) {
        User student = userRepository.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(NotFoundException::new);
        inviteRepository.deleteUnusedForUser(studentId);
        return issue(student.getDisplayName(), studentId, teacherId);
    }

    @Transactional(readOnly = true)
    public List<Invite> pendingNewStudents() {
        return inviteRepository.findPendingNewStudents();
    }

    /** Действующее приглашение по токену из ссылки. */
    @Transactional(readOnly = true)
    public Optional<Invite> findUsable(String token) {
        return inviteRepository.findByTokenHash(hash(token)).filter(i -> i.isUsable(Instant.now()));
    }

    @Transactional(readOnly = true)
    public Optional<String> loginOf(Invite invite) {
        return invite.getUserId() == null ? Optional.empty()
                : userRepository.findById(invite.getUserId()).map(User::getLogin);
    }

    /**
     * Регистрирует ученика (или задаёт новый пароль по ссылке сброса) и гасит ссылку.
     * Для сброса пароля login игнорируется.
     */
    @Transactional
    public User redeem(String token, String login, String password) {
        Invite invite = inviteRepository.findForUpdate(hash(token))
                .filter(i -> i.isUsable(Instant.now()))
                .orElseThrow(() -> new InviteException("Ссылка недействительна: она уже использована или истекла."));
        User user;
        if (invite.isPasswordReset()) {
            user = userRepository.findById(invite.getUserId()).orElseThrow(NotFoundException::new);
            user.setPasswordHash(passwordEncoder.encode(password));
        } else {
            String cleanLogin = login.trim();
            if (userRepository.existsByLoginIgnoreCase(cleanLogin)) {
                throw new InviteException("Этот логин уже занят, выберите другой.");
            }
            user = userRepository.save(new User(Role.STUDENT, invite.getStudentDisplayName(), cleanLogin,
                    passwordEncoder.encode(password)));
        }
        invite.setUsedAt(Instant.now());
        return user;
    }

    private Invite pending(Long inviteId) {
        return inviteRepository.findById(inviteId)
                .filter(i -> i.getUserId() == null && i.getUsedAt() == null)
                .orElseThrow(NotFoundException::new);
    }

    private String issue(String displayName, Long userId, Long teacherId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        inviteRepository.save(new Invite(hash(token), displayName, userId, Instant.now().plus(LIFETIME), teacherId));
        return token;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
