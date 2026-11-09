package com.techblog.platform.controller;

import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.PersonalizationService;
import com.techblog.platform.service.PersonalizationService.Navigation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PersonalizationController {

    private final PersonalizationService service;

    public PersonalizationController(PersonalizationService service) {
        this.service = service;
    }

    @GetMapping("/my/navigation")
    public Navigation navigation(@AuthenticationPrincipal UserPrincipal user) {
        return service.navigation(user.getId());
    }

    @PutMapping("/my/navigation")
    public Navigation save(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody Navigation request
    ) {
        return service.saveNavigation(user.getId(), request);
    }

    @GetMapping("/profiles/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id) {
        var avatar = service.avatar(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(avatar.contentType()))
            .cacheControl(CacheControl.noCache())
            .eTag(avatar.revision())
            .body(avatar.data());
    }

    @PostMapping(
        value = "/my/avatar",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, String> upload(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam MultipartFile file
    ) {
        return Map.of("revision", service.uploadAvatar(user.getId(), file));
    }

    @DeleteMapping("/my/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal UserPrincipal user) {
        service.removeAvatar(user.getId());
    }
}
