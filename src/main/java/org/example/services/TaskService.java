package org.example.services;

import org.example.models.Category;
import org.example.models.Task;
import org.example.models.TaskStatus;
import org.example.repo.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;


@Service
public class TaskService {
    private final TaskRepository taskRepository;

    TaskService(TaskRepository taskRepository){
        this.taskRepository = taskRepository;
    }

    public Optional<Task> getTaskById(Long taskId) {
        return taskRepository.findById(taskId);
    }


    public Task updateTaskStatus(Long taskId, TaskStatus newStatus){
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setTaskStatus(newStatus);
            return taskRepository.save(task);
        }
        return null;
    }
    public List<Task> getTaskListByUser(Long userId){
        return taskRepository.findByUserOwnerId(userId);
    }
    public boolean deleteTask(Long taskId) {
        if (taskRepository.existsById(taskId)) {
            taskRepository.deleteById(taskId);
            return true;
        }
        return false;
    }
    // Задачи с дедлайном в ближайшие 3 дня
    public List<Task> getTasksWithDeadlineApproaching(Long userId) {
        Date threeDaysLater = new Date(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000);
        return taskRepository.findByUserOwnerIdAndDeadlineTimeBefore(userId, threeDaysLater);
    }

    public Task updateTaskDeadline(Long taskId, Date newDeadline){
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setDeadlineTime(newDeadline);
            return taskRepository.save(task);
        }
        return null;
    }

    public List<Task> getTaskByUserIdAndStatus(Long userId, TaskStatus status){
        return taskRepository.findByUserOwnerIdAndTaskStatus(userId,status);
    }

    public List<Task> getTaskByUserIdAndCategory(Long userId, Category category){
        return taskRepository.findByUserOwnerIdAndCategory(userId,category);
    }

    public Task updateTask(Task task) {
        return taskRepository.save(task);
    }
    public Task createTask(String name, String description, Long userOwnerId, TaskStatus status, Date date, Category category){
        Task task = new Task(name,description,userOwnerId,status,date, category);
        return taskRepository.save(task);
    }

}
