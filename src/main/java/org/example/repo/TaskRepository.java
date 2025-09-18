package org.example.repo;

import org.example.models.Category;
import org.example.models.Task;
import org.example.models.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserOwnerId(Long userOwnerId);
    List<Task> findByUserOwnerIdAndTaskStatus(Long userOwnerId, TaskStatus status);
    List<Task> findByUserOwnerIdOrderByCreatedAtDesc(Long userOwnerId);

    List<Task> findByUserOwnerIdAndDeadlineTimeBefore(Long userOwnerId, Date date);
    List<Task> findByUserOwnerIdAndDeadlineTimeBetween(Long userOwnerId, Date startDate, Date endDate);
    List<Task> findByUserOwnerIdAndCategory(Long userId, Category category);
    @Query("SELECT t FROM Task t WHERE t.userOwnerId = :userId AND t.deadlineTime IS NOT NULL ORDER BY t.deadlineTime ASC")
    List<Task> findTasksWithDeadlineSorted(@Param("userId") Long userId);

    @Query("SELECT t FROM Task t WHERE t.userOwnerId = :userId AND t.deadlineTime < :now")
    List<Task> findOverdueTasks(@Param("userId") Long userId, @Param("now") Date now);
}