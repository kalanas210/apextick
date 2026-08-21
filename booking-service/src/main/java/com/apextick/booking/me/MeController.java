package com.apextick.booking.me;

import com.apextick.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Account")
public class MeController {

    @GetMapping("/api/me")
    @Operation(summary = "The authenticated user's profile and roles")
    public CurrentUser me(CurrentUser user) {
        return user;
    }
}
