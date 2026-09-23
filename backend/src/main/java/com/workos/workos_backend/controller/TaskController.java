package com.workos.workos_backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.dto.TaskResponse;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.service.TaskService;

import jakarta.validation.Valid;

/** Implements the Tasks endpoint table from BACKEND.md. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final ActingPersonResolver actingPersonResolver;

    public TaskController(TaskService taskService, ActingPersonResolver actingPersonResolver) {
        this.taskService = taskService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping
    public Map<String, List<TaskResponse>> list() {
        String actorId = actingPersonResolver.currentPersonId();
        Map<String, List<Task>> tasksByBoard = taskService.listVisibleTasksGroupedByBoard(actorId);
        Map<String, List<TaskResponse>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Task>> entry : tasksByBoard.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().map(TaskResponse::from).toList());
        }
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        Task task = taskService.createTask(actorId, request);
        return TaskResponse.from(task);
    }

    @PatchMapping("/{taskId}")
    public TaskResponse update(@PathVariable String taskId, @Valid @RequestBody UpdateTaskRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        Task task = taskService.updateTask(actorId, taskId, request);
        return TaskResponse.from(task);
    }

    @DeleteMapping("/{taskId}")
    public OkResponse delete(@PathVariable String taskId) {
        String actorId = actingPersonResolver.currentPersonId();
        taskService.deleteTask(actorId, taskId);
        return OkResponse.OK;
    }
}
