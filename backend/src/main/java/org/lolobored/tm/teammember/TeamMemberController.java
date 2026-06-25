package org.lolobored.tm.teammember;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/team-members")
public class TeamMemberController {

    private final TeamMemberRepository repository;

    public TeamMemberController(TeamMemberRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TeamMember> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<TeamMember> create(@Valid @RequestBody TeamMember teamMember) {
        teamMember.setId(null);
        TeamMember saved = repository.save(teamMember);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public TeamMember get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public TeamMember update(@PathVariable Long id, @Valid @RequestBody TeamMember teamMember) {
        TeamMember existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        teamMember.setId(id);
        teamMember.setPhoto(existing.getPhoto());
        teamMember.setPhotoContentType(existing.getPhotoContentType());
        return repository.save(teamMember);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @PostMapping("/{id}/photo")
    public ResponseEntity<Void> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        TeamMember teamMember = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be an image");
        }
        try {
            teamMember.setPhoto(file.getBytes());
            teamMember.setPhotoContentType(contentType);
            repository.save(teamMember);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        TeamMember teamMember = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!teamMember.hasPhoto()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(teamMember.getPhotoContentType()))
                .body(teamMember.getPhoto());
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable Long id) {
        TeamMember teamMember = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        teamMember.setPhoto(null);
        teamMember.setPhotoContentType(null);
        repository.save(teamMember);
    }
}
