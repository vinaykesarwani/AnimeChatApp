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
 * Authenticates STOMP CONNECT frames using the login/passcode headers.
 *
 * Without this, Spring Security only authenticates the HTTP upgrade (SockJS
 * handshake) request, but the resulting Principal is not automatically
 * propagated to the STOMP messaging channel for all users. Admin users were
 * silently failing because their Principal was null in @MessageMapping handlers,
 * causing a NullPointerException on principal.getName() which swallowed the
 * message and prevented sending/editing/deleting.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public WebSocketAuthChannelInterceptor(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String username = accessor.getLogin();
            String password = accessor.getPasscode();

            if (username != null && password != null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (passwordEncoder.matches(password, userDetails.getPassword())) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                    }
                } catch (Exception e) {
                    // Invalid credentials — leave user as null, connection will be rejected
                }
            }
        }

        return message;
    }
}
