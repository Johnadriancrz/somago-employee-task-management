package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.dto.CreateAccountRequest;
import com.workos.workos_backend.dto.PersonResponse;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.service.AccountService;
import com.workos.workos_backend.service.AdminAuthService;
import com.workos.workos_backend.service.AuthService;

import jakarta.validation.Valid;

/**
 * Employee account creation (Phase 3, spec section 5.2/14) - Admin-only.
 *
 * <p>Authorization is resolved entirely from server-verified session
 * cookies, never from anything in the request body:
 * <ul>
 *   <li>A valid {@link AdminAuthService#COOKIE_NAME} session -&gt; authorized.</li>
 *   <li>No valid Admin session, but a valid {@link AuthService#COOKIE_NAME}
 *       employee session (any of the 8 access roles, including CEO) -&gt;
 *       401/403 distinction below.</li>
 *   <li>No valid session of either kind -&gt; 401 Unauthorized.</li>
 * </ul>
 * Per Phase 3's Admin clarification, an authenticated employee - CEO
 * included - is never treated as Admin: that case is a known, authenticated
 * actor who simply lacks the required privilege, which is exactly what
 * {@link ForbiddenException} (403) means, as opposed to
 * {@link UnauthorizedException} (401) for no verified identity at all.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AdminAuthService adminAuthService;
    private final AuthService authService;

    public AccountController(AccountService accountService, AdminAuthService adminAuthService,
            AuthService authService) {
        this.accountService = accountService;
        this.adminAuthService = adminAuthService;
        this.authService = authService;
    }

    @PostMapping
    public PersonResponse createAccount(
            @CookieValue(name = AdminAuthService.COOKIE_NAME, required = false) String adminToken,
            @CookieValue(name = AuthService.COOKIE_NAME, required = false) String personToken,
            @Valid @RequestBody CreateAccountRequest request) {
        requireAuthenticatedAdmin(adminToken, personToken);
        Person created = accountService.createAccount(
                request.name(), request.email(), request.password(), request.accessRole());
        return PersonResponse.from(created);
    }

    /**
     * Admin-only roster of persisted employee accounts (spec section 5.2/14).
     * Same authorization rule as {@link #createAccount}: a valid Admin
     * session is required, an authenticated employee session (any of the 8
     * access roles, including CEO) is 403 not 200, and no session at all is
     * 401. Never returns {@code passwordHash} - {@link PersonResponse}
     * doesn't have a field for it.
     */
    @GetMapping
    public List<PersonResponse> listAccounts(
            @CookieValue(name = AdminAuthService.COOKIE_NAME, required = false) String adminToken,
            @CookieValue(name = AuthService.COOKIE_NAME, required = false) String personToken) {
        requireAuthenticatedAdmin(adminToken, personToken);
        return accountService.listAccounts().stream().map(PersonResponse::from).toList();
    }

    private void requireAuthenticatedAdmin(String adminToken, String personToken) {
        if (adminToken != null && isValidAdminSession(adminToken)) {
            return;
        }
        if (personToken != null && isValidPersonSession(personToken)) {
            throw new ForbiddenException("Admin privileges are required to create employee accounts");
        }
        throw new UnauthorizedException("Not authenticated");
    }

    private boolean isValidAdminSession(String token) {
        try {
            adminAuthService.currentAdmin(token);
            return true;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    private boolean isValidPersonSession(String token) {
        try {
            authService.currentPerson(token);
            return true;
        } catch (UnauthorizedException e) {
            return false;
        }
    }
}
