package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = { "shift", "assignedOffice", "role", "role.permissions", "directPermissions" })
    Optional<User> findByEmail(String email);

    Optional<User> findByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = { "role" })
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    List<User> findBySupervisorId(Long supervisorId);
    List<User> findBySupervisor(User supervisor);

    List<User> findByManagerId(Long managerId);
    List<User> findByManager(User manager);

    boolean existsByIdAndManagerId(Long id, Long managerId);

    @Query("SELECT u FROM User u WHERE ((u.manager IS NULL AND u.supervisor IS NULL) OR u.role.name = 'ADMIN' OR u.role.name = 'ROLE_ADMIN') AND u.role.name NOT IN ('ASSOCIATE', 'BDA')")
    List<User> findHierarchyRoots();

    @Query("SELECT u.id FROM User u WHERE u.manager.id = :id OR u.supervisor.id = :id")
    List<Long> findDirectReports(@Param("id") Long id);

    @Query("SELECT u.id FROM User u WHERE u.manager.id IN :ids OR u.supervisor.id IN :ids")
    List<Long> findDirectReportsByMultipleIds(@Param("ids") List<Long> ids);

    default List<Long> findSubordinateIds(Long managerId) {
        java.util.Set<Long> result = new java.util.HashSet<>();
        java.util.List<Long> currentLevel = findDirectReports(managerId);
        
        while (currentLevel != null && !currentLevel.isEmpty()) {
            result.addAll(currentLevel);
            currentLevel = findDirectReportsByMultipleIds(new java.util.ArrayList<>(currentLevel));
            currentLevel.removeAll(result);
        }
        return new java.util.ArrayList<>(result);
    }

    @Query("SELECT COUNT(u) FROM User u WHERE u.active = true AND (:isGlobal = true OR u.id IN :userIds) AND (u.joiningDate IS NULL OR u.joiningDate <= :date)")
    long countActiveUsersByDate(
            @Param("isGlobal") boolean isGlobal,
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("date") java.time.LocalDate date);

    @Query("SELECT u.id FROM User u")
    List<Long> findAllIds();

    org.springframework.data.domain.Page<User> findByIdIn(java.util.Collection<Long> ids,
            org.springframework.data.domain.Pageable pageable);

    // Manager → TL
    @Query("SELECT u.id FROM User u WHERE u.manager.id = :managerId")
    List<Long> findTeamLeadsByManager(@Param("managerId") Long managerId);

    // TL → Associates
    @Query("SELECT u.id FROM User u WHERE u.supervisor.id = :tlId")
    List<Long> findAssociatesByTl(@Param("tlId") Long tlId);

    // Bulk
    @Query("SELECT u.id FROM User u WHERE u.supervisor.id IN :tlIds")
    List<Long> findAssociatesByTlIds(@Param("tlIds") List<Long> tlIds);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName")
    List<User> findByRoleName(@Param("roleName") String roleName);
}
