package cn.edu.dorm;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SessionService {
    private final SessionRegistry registry;
    public SessionService(SessionRegistry registry) { this.registry = registry; }
    public void expireOthers(String username) {
        String current = null;
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            var session = servlet.getRequest().getSession(false);
            if (session != null) current = session.getId();
        }
        for (Object principal : registry.getAllPrincipals()) {
            if (!(principal instanceof UserDetails details) || !details.getUsername().equals(username)) continue;
            for (SessionInformation info : registry.getAllSessions(principal, false)) {
                if (!info.getSessionId().equals(current)) info.expireNow();
            }
        }
    }
}
