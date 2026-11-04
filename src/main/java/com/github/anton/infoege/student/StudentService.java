package com.github.anton.infoege.student;

import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;

    public StudentService(UserRepository userRepository, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    public List<User> all() {
        return userRepository.findByRoleOrderByDisplayName(Role.STUDENT);
    }

    @Transactional(readOnly = true)
    public List<User> active() {
        return userRepository.findByRoleAndActiveTrueOrderByDisplayName(Role.STUDENT);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(NotFoundException::new);
    }

    /** Деактивация запрещает вход и завершает уже открытые сессии ученика. */
    @Transactional
    public void setActive(Long id, boolean active) {
        User student = get(id);
        student.setActive(active);
        if (!active) {
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                if (principal instanceof AppUserDetails details && details.getId().equals(id)) {
                    sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
                }
            }
        }
    }
}
