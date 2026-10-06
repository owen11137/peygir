package com.novinkish.peygir.service;

import com.novinkish.peygir.PeygirApplication;
import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import com.novinkish.peygir.security.PeygirUser;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TeamSupervisionTest {
    private static ConfigurableApplicationContext context;
    private static AdminService admin;
    private static ReportService service;
    private static StatsService stats;
    private static UserRepository users;
    private static TeamRepository teams;
    private static ReportRepository reports;
    private static AppUser observer;
    private static Team home, first, second, outside;
    private static final List<Report> fixtures = new ArrayList<>();

    @BeforeAll
    static void start() {
        context = SpringApplication.run(PeygirApplication.class, "--server.port=0", "--server.ssl.enabled=false",
                "--spring.datasource.url=jdbc:h2:mem:supervision;DB_CLOSE_DELAY=-1", "--peygir.seed-demo=false",
                "--logging.level.root=WARN");
        admin = context.getBean(AdminService.class);
        service = context.getBean(ReportService.class);
        stats = context.getBean(StatsService.class);
        users = context.getBean(UserRepository.class);
        teams = context.getBean(TeamRepository.class);
        reports = context.getBean(ReportRepository.class);
        home = teams.save(new Team("home"));
        first = teams.save(new Team("first"));
        second = teams.save(new Team("second"));
        outside = teams.save(new Team("outside"));
        admin.createUser("observer", "Observer", home.getId(), List.of(), "Test@123", false,
                List.of(first.getId(), second.getId()));
        observer = users.findByUsername("observer").orElseThrow();
        // Deliberately give broad permissions: an explicit supervision scope must still protect other teams.
        observer.getGrantedPermissions().addAll(EnumSet.allOf(Permission.class));
        users.saveAndFlush(observer);
        AppUser actor = users.findByUsername("admin").orElseThrow();
        Reason reason = context.getBean(ReasonRepository.class).findAll().get(0);
        for (Team team : List.of(home, first, second, outside)) {
            for (Status status : Status.values()) {
                Report report = new Report();
                report.setCaller(actor);
                report.setCallerTeam(team);
                report.setTargetTeam(first); // Target-team membership alone must not reveal outside reports.
                report.setContactMethod(ContactMethod.PHONE);
                report.setContactAt(JalaliCalendar.now());
                report.setSubject(team.getName() + "-" + status);
                report.setReason(reason);
                report.setStatus(status);
                report.setCreatedAt(LocalDateTime.now());
                report.setUpdatedAt(LocalDateTime.now());
                fixtures.add(reports.saveAndFlush(report));
            }
        }
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    private PeygirUser principal() { return new PeygirUser(users.findById(observer.getId()).orElseThrow()); }
    private Report report(Team team, Status status) {
        return fixtures.stream().filter(r -> r.getCallerTeam().getId().equals(team.getId()) && r.getStatus() == status)
                .findFirst().orElseThrow();
    }

    @Test
    void listsExactlyAssignedTeamsAndMatchesDetailAuthorization() {
        PeygirUser user = principal();
        var visible = service.list(user, new ReportFilter(), false).stream().map(Report::getId).collect(Collectors.toSet());
        assertEquals(18, visible.size());
        for (Report report : fixtures) {
            boolean expected = !report.getCallerTeam().getId().equals(outside.getId()) && report.getStatus() != Status.DRAFT;
            assertEquals(expected, visible.contains(report.getId()), report.getSubject());
            assertEquals(expected, service.canView(user, report), report.getSubject());
        }
        assertThrows(ResponseStatusException.class, () -> service.getVisible(user, report(outside, Status.APPROVED).getId()));
    }

    @Test
    void dashboardAndItsExportDataExcludeOutsideAndUnvalidatedReports() {
        PeygirUser user = principal();
        var range = stats.resolve(user, "all", null, null);
        var data = stats.reports(user, range);
        assertEquals(9, data.size());
        assertTrue(data.stream().allMatch(r -> user.canReadTeam(r.getCallerTeam().getId()) && r.getStatus().isValidated()));
        assertEquals(9, stats.dashboard(user, range).kpis().total());
    }

    @Test
    void supervisedTeamsAreReadOnlyEvenWithWorkflowPermissions() {
        PeygirUser user = principal();
        Report pending = report(first, Status.PENDING), approved = report(second, Status.APPROVED);
        assertFalse(service.canEdit(user, pending));
        assertFalse(service.canDecide(user, pending));
        assertFalse(service.canReview(user, approved));
        assertFalse(service.canClose(user, approved));
        assertThrows(AccessDeniedException.class, () -> service.approve(user, pending.getId(), null));
        assertThrows(AccessDeniedException.class, () -> service.returnForFix(user, pending.getId(), "note"));
        assertThrows(AccessDeniedException.class, () -> service.reject(user, pending.getId(), "note"));
        assertThrows(AccessDeniedException.class, () -> service.startReview(user, approved.getId(), null));
        assertThrows(AccessDeniedException.class, () -> service.close(user, approved.getId(), null));
        assertEquals(Status.PENDING, reports.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(Status.APPROVED, reports.findById(approved.getId()).orElseThrow().getStatus());
        Report oldDraft = new Report();
        oldDraft.setCaller(observer);
        oldDraft.setCallerTeam(first);
        oldDraft.setStatus(Status.DRAFT);
        assertTrue(service.canView(user, oldDraft));
        assertFalse(service.canEdit(user, oldDraft), "Supervision cannot edit an old report from another team");
        assertTrue(service.canDecide(user, report(home, Status.PENDING)), "Existing primary-team duties are preserved");
    }

    @Test
    void selectionAloneGrantsReadOnlyDashboardAccess() {
        admin.createUser("readonly", "Read only", home.getId(), List.of(), "Test@123", false, List.of(first.getId()));
        PeygirUser user = new PeygirUser(users.findByUsername("readonly").orElseThrow());
        assertTrue(user.getPermissions().isEmpty());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PERM_TEAM_OVERVIEW")));
        assertFalse(user.has(Permission.TEAM_APPROVE));
        assertFalse(user.has(Permission.REVIEW_CLOSE));
    }

    @Test
    void rejectsUnknownTeamAndUpdatesScopeWithoutChangingPrimaryTeam() {
        AppUser actor = users.findByUsername("admin").orElseThrow();
        admin.createUser("scopechange", "Scope change", home.getId(), List.of(), "Test@123", false, List.of(first.getId()));
        AppUser user = users.findByUsername("scopechange").orElseThrow();
        assertThrows(BusinessException.class, () -> admin.updateUser(actor.getId(), user.getId(), "Scope change",
                home.getId(), List.of(), true, List.of(), false, List.of(Long.MAX_VALUE)));
        assertTrue(new PeygirUser(users.findById(user.getId()).orElseThrow()).canReadTeam(first.getId()));
        admin.updateUser(actor.getId(), user.getId(), "Scope change", home.getId(), List.of(), true, List.of(), false,
                List.of(second.getId(), second.getId()));
        var changed = new PeygirUser(users.findById(user.getId()).orElseThrow());
        assertEquals(home.getId(), changed.getTeamId());
        assertFalse(changed.canReadTeam(first.getId()));
        assertTrue(changed.canReadTeam(second.getId()));
        admin.updateUser(actor.getId(), user.getId(), "Scope change", home.getId(), List.of(), true, List.of(), false, List.of());
        assertFalse(new PeygirUser(users.findById(user.getId()).orElseThrow()).hasSupervisedTeams());
    }
}
