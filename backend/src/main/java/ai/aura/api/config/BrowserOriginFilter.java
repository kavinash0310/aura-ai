package ai.aura.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects browser requests that identify a cross-origin caller. */
public final class BrowserOriginFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public BrowserOriginFilter(String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        boolean crossSiteFetch = "cross-site".equalsIgnoreCase(fetchSite);
        boolean disallowedOrigin = origin != null && !allowedOrigins.contains(origin.trim());

        if (crossSiteFetch || disallowedOrigin) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin request rejected");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
