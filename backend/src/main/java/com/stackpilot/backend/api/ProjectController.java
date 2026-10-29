package com.stackpilot.backend.api;

import com.stackpilot.backend.api.dto.ApiRequests;
import com.stackpilot.backend.project.ProjectService;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public java.util.List<ProjectModels.ProjectRef> list() {
        return projects.list();
    }

    @PostMapping
    public ProjectModels.Snapshot attach(@RequestBody ApiRequests.AttachProjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return projects.attach(request.path());
    }

    @GetMapping("/{id}")
    public ProjectModels.Snapshot get(@PathVariable String id) {
        return projects.get(id);
    }

    /** Compact snapshot without file bodies — used by the desktop UI. */
    @GetMapping("/{id}/view")
    public ProjectModels.SnapshotView view(@PathVariable String id) {
        return projects.get(id).view();
    }

    @PostMapping("/{id}/index")
    public ProjectModels.Snapshot reindex(@PathVariable String id) {
        return projects.reindex(id);
    }
}
