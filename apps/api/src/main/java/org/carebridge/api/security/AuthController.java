package org.carebridge.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.carebridge.api.identity.Account;
import org.carebridge.api.identity.IdentityStore;
import org.carebridge.api.identity.UnitMembership;
import org.carebridge.api.web.ApiError;
import org.carebridge.api.web.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Session sign-in for staff and demo portal accounts")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final IdentityStore identities;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, IdentityStore identities) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.identities = identities;
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) @Schema(example = "maya.coordinator") String handle,
            @NotBlank @Size(max = 128) String password) {
    }

    public record UnitView(UUID id, @Schema(example = "DEMO-UNIT-01") String code, String name, String role) {

        static UnitView of(UnitMembership membership) {
            return new UnitView(membership.careUnitId(), membership.unitCode(), membership.unitName(),
                    membership.role().code());
        }
    }

    @Schema(description = "The signed-in account, its active care units and role permissions")
    public record Me(UUID id, String handle, String displayName,
            @Schema(example = "coordinator") String role, List<UnitView> units, List<String> permissions) {

        static Me of(Account account) {
            return new Me(account.id(), account.handle(), account.displayName(), account.role().code(),
                    account.memberships().stream().map(UnitView::of).toList(),
                    account.permissions().stream().sorted().toList());
        }
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue the XSRF-TOKEN cookie before the first POST")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with handle and password; starts a session cookie")
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.handle().trim(), body.password()));

        // New session id on every sign-in (session fixation).
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        CareBridgeUser user = (CareBridgeUser) authentication.getPrincipal();
        return Me.of(currentAccount(user, request));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in account, read fresh from the database")
    public Me me(@AuthenticationPrincipal CareBridgeUser user, HttpServletRequest request) {
        return Me.of(currentAccount(user, request));
    }

    /**
     * Wrong handle, wrong password, inactive account and account without password all produce the
     * same response, so sign-in cannot be used to find out which accounts exist.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> signInFailed(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("invalid_credentials",
                "Handle or password is incorrect.", RequestIdFilter.of(request)));
    }

    @ExceptionHandler(SessionRevokedException.class)
    ResponseEntity<ApiError> sessionRevoked(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("unauthenticated",
                "Sign in to continue.", RequestIdFilter.of(request)));
    }

    private Account currentAccount(CareBridgeUser user, HttpServletRequest request) {
        return identities.findById(user.id())
                .filter(Account::canSignIn)
                .orElseThrow(() -> {
                    // Deactivated since sign-in: end the session.
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    contextHolder.clearContext();
                    return new SessionRevokedException();
                });
    }

    static final class SessionRevokedException extends AuthenticationException {
        SessionRevokedException() {
            super("account is no longer active");
        }
    }
}
