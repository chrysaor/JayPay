package com.jaypay.auth.adapter.in.web;

import com.jaypay.auth.application.port.in.AccountChange;
import com.jaypay.auth.application.port.in.NewStaffAccount;
import com.jaypay.auth.application.port.in.PasswordChange;
import com.jaypay.auth.application.port.in.StaffAccountsUseCase;
import com.jaypay.auth.domain.StaffAccountException;
import com.jaypay.common.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jaypay.auth.domain.StaffAccountException.Reason.NOT_FOUND;

@WebAdapter
@RestController
@RequiredArgsConstructor
class StaffController {

    private final StaffAccountsUseCase staffAccounts;
    private final AuthenticationManager staffSignIn;

    @PostMapping("/staff/sign-in")
    StaffResponses.SignIn signIn(@RequestBody StaffRequests.SignIn request) {
        Authentication authentication = staffSignIn.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        Set<String> factors = authentication.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return StaffResponses.SignIn.of(staffAccounts.signIn(authentication.getName(), factors));
    }

    @GetMapping("/staff/me")
    StaffResponses.Account me(Authentication authentication) {
        return staffAccounts.find(authentication.getName())
                .map(StaffResponses.Account::of)
                .orElseThrow(() -> new StaffAccountException(NOT_FOUND));
    }

    @PutMapping("/staff/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(Authentication authentication, @RequestBody StaffRequests.ChangePassword request) {
        staffAccounts.changePassword(new PasswordChange(authentication.getName(), request.currentPassword(), request.newPassword()));
    }

    @GetMapping("/staff/accounts")
    List<StaffResponses.Account> accounts() {
        return staffAccounts.list().stream().map(StaffResponses.Account::of).toList();
    }

    @PostMapping("/staff/accounts")
    StaffResponses.Account create(@RequestBody StaffRequests.CreateAccount request) {
        return StaffResponses.Account.of(staffAccounts.create(
                new NewStaffAccount(request.username(), request.password(), request.role(), request.mfaEnabled())));
    }

    @PutMapping("/staff/accounts/{username}")
    StaffResponses.Account change(Authentication authentication, @PathVariable String username, @RequestBody StaffRequests.ChangeAccount request) {
        return StaffResponses.Account.of(staffAccounts.change(
                new AccountChange(authentication.getName(), username, request.role(), request.active(), request.mfaEnabled())));
    }
}
