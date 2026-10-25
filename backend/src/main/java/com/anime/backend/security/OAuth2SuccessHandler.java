package com.anime.backend.security;

import com.anime.backend.entity.User;
import com.anime.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            JwtUtil jwtUtil,
            UserService userService,
            @Value("${frontend.url}") String frontendUrl) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        User user;
        try {
            OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
            OAuth2User principal = oauth.getPrincipal();

            // Provider IDs belong to different namespaces, even when their values match.
            user = switch (oauth.getAuthorizedClientRegistrationId()) {
                case "google" -> userService.findOrCreateGoogleUser(
                        principal.getAttribute("sub"), principal.getAttribute("email"),
                        principal.getAttribute("name"));
                case "github" -> userService.findOrCreateGithubUser(
                        attribute(principal, "id"), principal.getAttribute("login"));
                default -> throw new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_provider"));
            };
        } catch (OAuth2AuthenticationException exception) {
            String error = "account_link_required".equals(exception.getError().getErrorCode())
                    ? "account_link_required" : "oauth_login_failed";
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/oauth2/callback?error=" + error);
            return;
        }

        String token = jwtUtil.generateToken(user.getUsername());
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/oauth2/callback?token=" + token);
    }

    private String attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        return value == null ? null : value.toString();
    }
}