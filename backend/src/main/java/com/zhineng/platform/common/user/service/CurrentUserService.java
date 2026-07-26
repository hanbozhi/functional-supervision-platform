package com.zhineng.platform.common.user.service;

import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final String mockCurrentUsername;

    public CurrentUserService(
            UserRepository userRepository,
            @Value("${app.security.mock-current-username:zhang.zhuren}")
            String mockCurrentUsername
    ) {
        this.userRepository = userRepository;
        this.mockCurrentUsername = mockCurrentUsername;
    }

    public CurrentUserResponse getCurrentUser() {
        UserRepository.UserRecord user = userRepository
                .findActiveByUsername(mockCurrentUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Configured mock current user does not exist: " + mockCurrentUsername
                ));
        return new CurrentUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.orgUnitId(),
                user.orgUnitName(),
                userRepository.findActiveRoleCodes(user.id())
        );
    }
}
