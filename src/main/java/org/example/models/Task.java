package org.example.models;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TaskStatus taskStatus;

    private Double timeAmount;
    private String name;
    private String description;
    private Long userOwnerId;
    private Category category;

    @Temporal(TemporalType.TIMESTAMP)
    private Date deadlineTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    //constructors
    public Task() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Task(String name, String description, Long userOwnerId, TaskStatus taskStatus, Date deadlineTime, Category category) {
        this();
        this.name = name;
        this.description = description;
        this.userOwnerId = userOwnerId;
        this.taskStatus = taskStatus;
        this.deadlineTime = deadlineTime;
        this.category = category;
    }

    // getters+setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category){
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }

    public TaskStatus getTaskStatus() { return taskStatus; }
    public void setTaskStatus(TaskStatus taskStatus) {
        this.taskStatus = taskStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public Double getTimeAmount() { return timeAmount; }
    public void setTimeAmount(Double timeAmount) {
        this.timeAmount = timeAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserOwnerId() { return userOwnerId; }
    public void setUserOwnerId(Long userOwnerId) {
        this.userOwnerId = userOwnerId;
        this.updatedAt = LocalDateTime.now();
    }

    public Date getDeadlineTime(){ return deadlineTime; }
    public void setDeadlineTime(Date deadlineTime){
        this.deadlineTime = deadlineTime;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}