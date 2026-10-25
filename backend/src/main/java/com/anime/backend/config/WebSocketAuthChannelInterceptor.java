package com.anime.backend.config;

import com.anime.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MessageChannel clientOutboundChannel;

    public WebSocketAuthChannelInterceptor(UserDetailsService userDetailsService,
                                           PasswordEncoder passwordEncoder,
                                           JwtUtil jwtUtil,
                                           @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try {
            return authenticateAndAuthorize(message);
        } catch (AuthenticationException | AccessDeniedException exception) {
            // Ordered inbound delivery logs and consumes interceptor exceptions.
            // Send a session-specific ERROR so the client is notified and Spring closes the socket.
            StompHeaderAccessor incoming = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
            error.setSessionId(incoming.getSessionId());
            error.setMessage("Chat request rejected");
            clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
            throw exception;
        }
    }

    private Message<?> authenticateAndAuthorize(Message<?> message) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            // ── 1. Try JWT Bearer token (sent as the passcode with login="__jwt__") ──
            String login    = accessor.getLogin();
            String passcode = accessor.getPasscode();

            if ("__jwt__".equals(login)) {
                if (passcode == null || !jwtUtil.validateToken(passcode)) {
                    throw new BadCredentialsException("Invalid credentials");
                }
                String username = jwtUtil.extractUsername(passcode);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                accessor.setUser(auth);

            // ── 2. Fallback: legacy username/password (keeps existing users working) ──
            } else if (login != null && passcode != null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(login);
                    if (passwordEncoder.matches(passcode, userDetails.getPassword())) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                    }
                } catch (Exception exception) {
                    throw new BadCredentialsException("Invalid credentials", exception);
                }
            }
            if (accessor.getUser() == null) {
                throw new BadCredentialsException("Authentication required");
            }
        } else if (accessor != null && (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))) {
            if (!(accessor.getUser() instanceof Authentication auth) || !auth.isAuthenticated()) {
                throw new AccessDeniedException("Authentication required");
            }
            String destination = accessor.getDestination();
            boolean allowed = destination != null && (StompCommand.SEND.equals(accessor.getCommand())
                    ? destination.matches("/app/chat\\.send/[1-9][0-9]*")
                        || destination.equals("/app/chat.edit") || destination.equals("/app/chat.delete")
                    : destination.matches("/topic/chat/[1-9][0-9]*")
                        || destination.equals("/user/queue/errors"));
            if (!allowed) throw new AccessDeniedException("Destination not allowed");
        }

        return message;
    }
}
