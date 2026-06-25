package org.lolobored.tm.assignment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {
    private final AssignmentRepository repository;

    public AssignmentController(AssignmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Assignment> list(
            @RequestParam(required = false) Long teamMemberId,
            @RequestParam(required = false) Long customerId) {
        if (teamMemberId != null) return repository.findByTeamMemberId(teamMemberId);
        else if (customerId != null) return repository.findByCustomerId(customerId);
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Assignment> create(@Valid @RequestBody Assignment assignment) {
        assignment.setId(null);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(assignment));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assignment already exists for this team member/customer/month");
        }
    }

    @GetMapping("/{id}")
    public Assignment get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Assignment update(@PathVariable Long id, @Valid @RequestBody Assignment assignment) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        assignment.setId(id);
        try {
            return repository.save(assignment);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assignment already exists for this team member/customer/month");
        }
    }

    @PatchMapping("/{id}")
    public Assignment patch(@PathVariable Long id, @RequestBody java.util.Map<String, Object> updates) {
        Assignment existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (updates.containsKey("usagePercent")) {
            existing.setUsagePercent(((Number) updates.get("usagePercent")).intValue());
        }
        if (updates.containsKey("status")) {
            try {
                existing.setStatus(AssignmentStatus.valueOf((String) updates.get("status")));
            } catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }
        try {
            return repository.save(existing);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment conflict");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repository.deleteById(id);
    }
}
