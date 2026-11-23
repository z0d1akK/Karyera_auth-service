package com.zodiakk.authservice.auth.security.jwt;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentAccessTokenGuard implements AccessTokenGuard {

    private final UserRepository userRepository;

    private final AuthCredentialRepository authCredentialRepository;

    @Override
    public boolean allows(JwtTokenClaims claims) {
        User user = userRepository.findById(claims.userId()).orElse(null);
        if (user == null
                || !Boolean.TRUE.equals(user.getActive())
                || user.getDeletionStatus() != AccountDeletionStatus.NONE
                || !user.getEmail().equals(claims.email())) {
            return false;
        }

        AuthCredential credential = authCredentialRepository.findByUserId(user.getId()).orElse(null);
        if (credential == null || Boolean.TRUE.equals(credential.getPasswordChangeRequired())) {
            return false;
        }

        return credential.getRole() == claims.role();
    }
}
