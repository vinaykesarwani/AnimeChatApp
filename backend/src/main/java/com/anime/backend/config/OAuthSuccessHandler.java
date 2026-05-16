package com.anime.backend.config;

import com.anime.backend.entity.User;
import com.anime.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Invoked by Spring Security after a successful Google OAuth2 login.
 *
 * Flow:
 *   Google → /login/oauth2/code/google → Spring Security validates token
 *   → this handler → find-or-create User → mint JWT → redirect to frontend
 */
@Component
public class OAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final String frontendUrl;

    public OAuthSuccessHandler(
            UserService userService,
            JwtUtil jwtUtil,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // Google provides: sub (unique ID), email, name, picture
        String googleId = oauthUser.getAttribute("sub");
        String email    = oauthUser.getAttribute("email");
        String name     = oauthUser.getAttribute("name");

        // Find existing user or create a new one
        User user = userService.findOrCreateGoogleUser(googleId, email, name);

        // Mint a JWT for this user
        String token = jwtUtil.generateToken(user.getUsername());

        // Redirect to the frontend callback page with the token as a query param.
        // The frontend will read it, store it, and redirect to the homepage.
        String redirectUrl = frontendUrl + "/oauth/callback?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
