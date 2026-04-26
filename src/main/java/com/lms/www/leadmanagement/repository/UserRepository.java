package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);
    List<User> findByRoleName(String roleName);
    List<User> findBySupervisor(User supervisor);
    List<User> findByManager(User manager);

    @Query("SELECT u FROM User u WHERE (u.manager IS NULL AND u.supervisor IS NULL) OR u.role.name = 'ADMIN'")
    List<User> findHierarchyRoots();

    // Fetch full hierarchy of subordinates using a Recursive CTE (MySQL 8.0+)
    // Includes cycle detection and depth limit to prevent infinite recursion
    @Query(value = "WITH RECURSIVE Subordinates AS (" +
                   "  SELECT id, CAST(id AS CHAR(1000)) as path, 1 as depth FROM users WHERE id = :managerId " +
                   "  UNION ALL " +
                   "  SELECT u.id, CONCAT(s.path, ',', u.id), s.depth + 1 " +
                   "  FROM users u " +
                   "  INNER JOIN Subordinates s ON (u.manager_id = s.id OR u.supervisor_id = s.id) " +
                   "  WHERE FIND_IN_SET(u.id, s.path) = 0 AND s.depth < 20 " +
                   ") SELECT DISTINCT id FROM Subordinates WHERE id != :managerId",
           nativeQuery = true)
    List<Long> findSubordinateIds(@Param("managerId") Long managerId);

    org.springframework.data.domain.Page<User> findByIdIn(java.util.Collection<Long> ids, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.active = true AND (u.joiningDate IS NULL OR u.joiningDate <= :date)")
    long countActiveUsersByDate(@Param("date") java.time.LocalDate date);

    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN :userIds AND u.active = true AND (u.joiningDate IS NULL OR u.joiningDate <= :date)")
    long countActiveUsersByDateIn(@Param("userIds") java.util.Collection<Long> userIds, @Param("date") java.time.LocalDate date);
}
