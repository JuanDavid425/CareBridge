package org.carebridge.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.carebridge.api.web.ApiError;
import org.carebridge.api.web.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 401 and 403 responses in the {@link ApiError} envelope instead of HTML or a redirect. */
@Component
class JsonSecurityHandlers {

    private final ObjectMapper json;

    JsonSecurityHandlers(ObjectMapper json) {
        this.json = json;
    }

    AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(request, response, HttpStatus.UNAUTHORIZED,
                "unauthenticated", "Sign in to continue.");
    }

    AccessDeniedHandler accessDenied() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(request, response, HttpStatus.FORBIDDEN, "csrf_token_invalid",
                        "Missing or invalid CSRF token. Reload the page and try again.");
            } else {
                write(request, response, HttpStatus.FORBIDDEN, ApiError.forbidden(RequestIdFilter.of(request)));
            }
        };
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message) throws IOException {
        write(request, response, status, ApiError.of(code, message, RequestIdFilter.of(request)));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, ApiError body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
