package dev.varshit.proctor.user.controller;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.common.dto.UserDTO;
import dev.varshit.proctor.common.dto.UserInfoDTO;
import dev.varshit.proctor.notifications.progress.ProgressBroker;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.CurrentUser;
import dev.varshit.proctor.user.dto.CreateUserRequest;
import dev.varshit.proctor.user.dto.PatchUserRequest;
import dev.varshit.proctor.user.dto.UpdateUserRequest;
import dev.varshit.proctor.user.imports.ImportTemplateGenerator;
import dev.varshit.proctor.user.imports.UserImportService;
import dev.varshit.proctor.user.service.UserProfileService;
import dev.varshit.proctor.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserProfileService profileService;
    private final UserImportService importService;
    private final ImportTemplateGenerator templateGenerator;
    private final ProgressBroker events;

    public UserController(UserService userService, UserProfileService profileService,
                          UserImportService importService, ImportTemplateGenerator templateGenerator,
                          ProgressBroker events) {
        this.userService = userService;
        this.profileService = profileService;
        this.importService = importService;
        this.templateGenerator = templateGenerator;
        this.events = events;
    }

    @GetMapping
    public Mono<PageResponse<UserInfoDTO>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return userService.query(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/me")
    public Mono<UserDTO> me() {
        return profileService.me();
    }

    @GetMapping(value = "/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> events() {
        return CurrentUser.require().flatMapMany(principal -> events.subscribe(principal.id()));
    }

    @GetMapping("/me/roles")
    public Flux<RoleWithPermissionDTO> myRoles() {
        return profileService.myRoles();
    }

    @GetMapping("/me/permissions")
    public Flux<PermissionDTO> myPermissions() {
        return profileService.myPermissions();
    }

    @GetMapping("/me/departments")
    public Flux<DepartmentDTO> myDepartments() {
        return profileService.myDepartments();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserInfoDTO> create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<List<UserInfoDTO>> createBulk(@RequestBody List<@Valid CreateUserRequest> requests) {
        return userService.createBulk(requests);
    }

    @GetMapping("/import/template")
    public Mono<ResponseEntity<byte[]>> importTemplate(@RequestParam(defaultValue = "csv") String format) {
        return templateGenerator.generate(format)
                .map(template -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(template.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(template.fileName()).build().toString())
                        .body(template.bytes()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, Object>> importUsers(@RequestPart("file") FilePart file) {
        return CurrentUser.require()
                .flatMap(principal -> importService.start(file, principal))
                .map(started -> Map.<String, Object>of(
                        "importing", true,
                        "message", "Import started",
                        "processId", started.processId(),
                        "total", started.total()));
    }

    @DeleteMapping("/bulk")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteBulk(@RequestBody Set<UUID> ids) {
        return userService.delete(ids);
    }

    @GetMapping("/{id}")
    public Mono<UserDTO> get(@PathVariable UUID id) {
        return profileService.get(id);
    }

    @PutMapping("/{id}")
    public Mono<UserInfoDTO> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}")
    public Mono<UserInfoDTO> patch(@PathVariable UUID id, @Valid @RequestBody PatchUserRequest request) {
        return userService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return userService.delete(Set.of(id));
    }

    @GetMapping("/{id}/roles")
    public Flux<RoleWithPermissionDTO> roles(@PathVariable UUID id) {
        return profileService.roles(id);
    }

    @GetMapping("/{id}/permissions")
    public Flux<PermissionDTO> permissions(@PathVariable UUID id) {
        return profileService.permissions(id);
    }

    @GetMapping("/{id}/departments")
    public Flux<DepartmentDTO> departments(@PathVariable UUID id) {
        return profileService.departments(id);
    }
}
