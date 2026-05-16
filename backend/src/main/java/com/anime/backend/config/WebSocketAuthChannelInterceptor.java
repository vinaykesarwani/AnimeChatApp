package com.anime.backend.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP CONNECT frames.
 *
 * Supports two modes:
 *
 *   1. JWT mode (used by all frontend clients after this change):
 *      login   = "jwt"
 *      passcode = <the JWT token>
 *
 *   2. Password mode (legacy fallback — kept for any direct STOMP clients):
 *      login   = <username>
 *      passcode = <raw password>
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public WebSocketAuthChannelInterceptor(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String login    = accessor.getLogin();
            String passcode = accessor.getPasscode();

            if (login != null && passcode != null) {
                try {
                    if ("jwt".equals(login)) {
                        // ── JWT mode ──────────────────────────────────────
                        if (jwtUtil.validateToken(passcode)) {
                            String username = jwtUtil.extractUsername(passcode);
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());
                            accessor.setUser(auth);
                        }
                    } else {
                        // ── Password mode (legacy) ─────────────────────────
                        UserDetails userDetails = userDetailsService.loadUserByUsername(login);
                        if (passwordEncoder.matches(passcode, userDetails.getPassword())) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());
                            accessor.setUser(auth);
                        }
                    }
                } catch (Exception e) {
                    // Invalid credentials — leave user as null, connection will be rejected
                }
            }
        }

        return message;
    }
}
