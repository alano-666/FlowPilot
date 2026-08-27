package com.flowpilot.service;

import com.flowpilot.auth.JwtService;
import com.flowpilot.common.BizException;
import com.flowpilot.model.User;
import com.flowpilot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证与用户管理服务。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 5 * 60_000L;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 登录防爆破：key = ip:username，value = 窗口期内失败时间戳队列 */
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public Map<String, Object> login(String username, String password, String clientIp) {
        String fk = (clientIp == null ? "unknown" : clientIp) + ":" + username;
        if (isBlocked(fk)) {
            log.warn("登录防爆破拦截: {}", fk);
            throw new BizException(42900, "登录失败次数过多，请 5 分钟后再试");
        }
        try {
            return doLogin(username, password);
        } catch (BizException e) {
            if (e.getCode() == 40102 || e.getCode() == 40103) {
                recordFailure(fk);
            }
            throw e;
        }
    }

    private Map<String, Object> doLogin(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(40102, "用户名或密码错误"));
        if (!user.isActive()) {
            throw new BizException(40103, "账号已停用");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BizException(40102, "用户名或密码错误");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole().name());
        return Map.of(
                "token", token,
                "user", toMap(user));
    }

    private boolean isBlocked(String key) {
        Deque<Long> q = failures.get(key);
        if (q == null || q.isEmpty()) {
            return false;
        }
        prune(q);
        return q.size() >= MAX_FAILURES;
    }

    private void recordFailure(String key) {
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(System.currentTimeMillis());
        }
    }

    private void prune(Deque<Long> q) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.pollFirst();
        }
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BizException(40406, "用户不存在: " + id));
    }

    public List<User> listUsers() {
        return userRepository.findByActiveTrueOrderByIdAsc();
    }

    @Transactional
    public User createUser(String username, String password, String displayName, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BizException(40909, "用户名已存在: " + username);
        }
        if (password == null || password.length() < 6) {
            throw new BizException(40008, "密码至少 6 位");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
        u.setRole(User.Role.valueOf(role.toUpperCase()));
        userRepository.save(u);
        return u;
    }

    @Transactional
    public User updateUser(Long id, String displayName, String role, String feishuOpenId,
                           String wecomUserId, String phone, Boolean active) {
        User u = getById(id);
        if (displayName != null) {
            u.setDisplayName(displayName);
        }
        if (role != null) {
            u.setRole(User.Role.valueOf(role.toUpperCase()));
        }
        if (feishuOpenId != null) {
            u.setFeishuOpenId(feishuOpenId);
        }
        if (wecomUserId != null) {
            u.setWecomUserId(wecomUserId);
        }
        if (phone != null) {
            u.setPhone(phone);
        }
        if (active != null) {
            u.setActive(active);
        }
        return userRepository.save(u);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(40008, "密码至少 6 位");
        }
        User u = getById(id);
        u.setPasswordHash(encoder.encode(newPassword));
        userRepository.save(u);
    }

    public Map<String, Object> toMap(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "displayName", u.getDisplayName(),
                "role", u.getRole().name(),
                "feishuOpenId", u.getFeishuOpenId() == null ? "" : u.getFeishuOpenId(),
                "wecomUserId", u.getWecomUserId() == null ? "" : u.getWecomUserId(),
                "phone", maskPhone(u.getPhone()));
    }

    /** 敏感信息脱敏（PRD 7.3）：138****5678 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
