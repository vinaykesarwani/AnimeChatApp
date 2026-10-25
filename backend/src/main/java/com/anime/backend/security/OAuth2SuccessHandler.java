package com.anime.backend.security;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            @Value("${frontend.url}") String frontendUrl) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // Extract provider-agnostic fields
        // Google: "sub" as ID, "email" for username seed
        // GitHub: "id" as ID, "login" for username seed
        String oauthId    = String.valueOf(
                oauthUser.getAttribute("sub") != null
                        ? oauthUser.getAttribute("sub")
                        : oauthUser.getAttribute("id"));

        String emailOrLogin = oauthUser.getAttribute("email") != null
                ? (String) oauthUser.getAttribute("email")
                : (String) oauthUser.getAttribute("login");

        // Derive a clean username: take part before '@', replace non-word chars
        String baseUsername = emailOrLogin.split("@")[0].replaceAll("[^\\w]", "_");

        // Find or create the user keyed by oauthId
        User user = userRepository.findByOauthId(oauthId).orElseGet(() -> {
            // Ensure unique username
            String username = baseUsername;
            int suffix = 1;
            while (userRepository.findByUsername(username).isPresent()) {
                username = baseUsername + "_" + suffix++;
            }
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPassword("");          // no password for OAuth users
            newUser.setRole(Role.USER);
            newUser.setOauthId(oauthId);
            return userRepository.save(newUser);
        });

        String token = jwtUtil.generateToken(user.getUsername());
        // Redirect to React callback route with the token as a query param
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/oauth2/callback?token=" + token);
    }
}