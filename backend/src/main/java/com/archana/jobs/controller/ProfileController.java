package com.archana.jobs.controller;

import com.archana.jobs.model.Profile;
import com.archana.jobs.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ProfileController {

    private static final Long PROFILE_ID = 1L;

    private final ProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Profile> getProfile() {
        return ResponseEntity.ok(
            profileRepository.findById(PROFILE_ID).orElseGet(() -> {
                Profile empty = new Profile();
                empty.setId(PROFILE_ID);
                return empty;
            })
        );
    }

    @PutMapping
    public ResponseEntity<Profile> saveProfile(@RequestBody Profile profile) {
        profile.setId(PROFILE_ID);
        return ResponseEntity.ok(profileRepository.save(profile));
    }
}
