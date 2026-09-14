package com.coderplatform.auth;

import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {

    public Optional<User> optional() {
        return Optional.ofNullable(AuthContext.getUser());
    }

    public User require() {
        User user = AuthContext.getUser();
        if (user == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    public boolean isAdmin() {
        User user = AuthContext.getUser();
        return user != null && user.isAdmin();
    }
}
