package org.lolobored.tm.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByTeamMemberId(Long teamMemberId);
    List<Assignment> findByCustomerId(Long customerId);
}
