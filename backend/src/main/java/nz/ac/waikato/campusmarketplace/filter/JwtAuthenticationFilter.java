package nz.ac.waikato.campusmarketplace.filter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nz.ac.waikato.campusmarketplace.service.AuthService;
import nz.ac.waikato.campusmarketplace.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AuthService auth;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtService jwt, AuthService auth,
                                   @Value("${app.cookie.name}") String cookieName) {
        this.jwt = jwt;
        this.auth = auth;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String token = readCookie(req, cookieName);
        if (token != null) {
            try {
                JwtService.ParsedToken p = jwt.parse(token);
                if (!auth.isBlacklisted(p.jti())) {
                    AuthPrincipal principal = new AuthPrincipal(p.userId(), p.email(), p.nickname());
                    UsernamePasswordAuthenticationToken authn =
                            new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authn);
                }
            } catch (JwtException ignored) {
                // expired or tampered: leave SecurityContext empty so controllers will 401
            }
        }
        chain.doFilter(req, res);
    }

    private String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
