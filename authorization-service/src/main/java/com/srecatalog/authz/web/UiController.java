package com.srecatalog.authz.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.authz.directory.DirectoryMapper;
import com.srecatalog.authz.directory.UserDirectory;
import com.srecatalog.authz.service.SubjectAccess;
import com.srecatalog.authz.web.dto.UserDirectoryResponse;
import com.srecatalog.authz.web.dto.UserDirectoryRow;
import com.srecatalog.common.model.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
public class UiController {

    private static final HttpHeaders NO_CACHE = new HttpHeaders();

    static {
        NO_CACHE.setCacheControl("no-cache");
    }

    private final UserDirectory userDirectory;
    private final RequestSubjectResolver subjectResolver;

    public UiController(UserDirectory userDirectory, RequestSubjectResolver subjectResolver) {
        this.userDirectory = userDirectory;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/index.html");
    }

    @GetMapping("/api/ui/users")
    public UserDirectoryResponse listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String group,
            HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        SubjectAccess.requirePlatformAdmin(subject);

        List<UserDirectoryRow> rows = DirectoryMapper.buildUserDirectoryRows(userDirectory);

        if (role != null && !role.isBlank()) {
            String roleUpper = role.toUpperCase(Locale.ROOT);
            rows = rows.stream().filter(row -> row.roles().contains(roleUpper)).toList();
        }

        if (group != null && !group.isBlank()) {
            String groupUpper = group.toUpperCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(row -> containsIgnoreCase(row.groups(), groupUpper)
                            || containsIgnoreCase(row.amountClubs(), groupUpper)
                            || containsIgnoreCase(row.coveringLobs(), groupUpper))
                    .toList();
        }

        if (q != null && !q.isBlank()) {
            String needle = q.strip().toLowerCase(Locale.ROOT);
            rows = rows.stream().filter(row -> matchesQuery(row, needle)).toList();
        }

        return new UserDirectoryResponse(rows.size(), userDirectory.emailDomain(), rows);
    }

    private static boolean matchesQuery(UserDirectoryRow row, String needle) {
        return row.userId().toLowerCase(Locale.ROOT).contains(needle)
                || row.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || row.loginName().toLowerCase(Locale.ROOT).contains(needle)
                || row.title().toLowerCase(Locale.ROOT).contains(needle)
                || containsNeedle(row.roles(), needle)
                || containsNeedle(row.groups(), needle)
                || containsNeedle(row.amountClubs(), needle)
                || containsNeedle(row.coveringLobs(), needle)
                || (row.lob() != null && row.lob().toLowerCase(Locale.ROOT).contains(needle))
                || (row.supervisorId() != null && row.supervisorId().toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean containsNeedle(List<String> values, String needle) {
        return values.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        return values.stream().anyMatch(value -> needle.equals(value.toUpperCase(Locale.ROOT)));
    }

    private static ResponseEntity<ClassPathResource> html(String path) {
        return ResponseEntity.ok()
                .headers(NO_CACHE)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource(path));
    }
}
