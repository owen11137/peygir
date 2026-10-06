package com.novinkish.peygir.service;

import com.novinkish.peygir.PeygirApplication;
import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RosterReplacementTest {
    private static ConfigurableApplicationContext context;
    private static UserRosterService roster;
    private static UserRepository users;
    private static TeamRepository teams;
    private static RoleRepository roles;
    private static ReportRepository reports;
    private static ReportEventRepository events;
    private static PasswordEncoder encoder;
    private static TransactionTemplate transaction;
    private AppUser admin;
    private Team home;

    @BeforeAll
    static void start() {
        context = SpringApplication.run(PeygirApplication.class, "--server.port=0", "--server.ssl.enabled=false",
                "--spring.datasource.url=jdbc:h2:mem:roster;DB_CLOSE_DELAY=-1", "--peygir.seed-demo=false",
                "--logging.level.root=WARN");
        roster = context.getBean(UserRosterService.class);
        users = context.getBean(UserRepository.class);
        teams = context.getBean(TeamRepository.class);
        roles = context.getBean(RoleRepository.class);
        reports = context.getBean(ReportRepository.class);
        events = context.getBean(ReportEventRepository.class);
        encoder = context.getBean(PasswordEncoder.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @BeforeEach
    void fixtures() {
        transaction.executeWithoutResult(status -> {
            events.deleteAll();
            reports.deleteAll();
            users.deleteAll(users.findAll().stream().filter(u -> !"admin".equals(u.getUsername())).toList());
            users.flush();
            admin = users.findByUsername("admin").orElseThrow();
            teams.deleteAll(teams.findAll().stream().filter(t -> !t.getId().equals(admin.getTeam().getId())).toList());
            teams.flush();
            home = teams.save(new Team("Roster Home"));
        });
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    private AppUser user(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName("Person " + username);
        user.setTeam(home);
        user.getRoles().add(roles.findByCode("USER").orElseThrow());
        user.setPasswordHash(encoder.encode("Keep@123"));
        return users.saveAndFlush(user);
    }

    private ByteArrayInputStream workbook(boolean optional, String[]... data) throws Exception {
        String[] titles = optional
                ? new String[]{"تیم", "نام", "نام کاربری", "نقش", "رمز", "نام کاربری قبلی", "تیم‌های تحت نظارت"}
                : new String[]{"تیم", "نام", "نام کاربری", "نقش", "رمز"};
        return workbookWithHeaders(titles, data);
    }

    private ByteArrayInputStream workbookWithHeaders(String[] titles, String[]... data) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("کاربران");
            var header = sheet.createRow(0);
            for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);
            for (int i = 0; i < data.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private UserRosterService.ReplacementResult replace(boolean optional, String[]... data) throws Exception {
        return roster.replaceUsers(workbook(optional, data), admin.getId(), false);
    }

    @Test
    void renamesIdentityAndKeepsHistoryPasswordsAndAdminsWhileDeactivatingOutsideUsers() throws Exception {
        AppUser renamed = user("u120"), outside = user("outside"), alreadyInactive = user("inactive");
        renamed.setMustChangePassword(true);
        renamed.getGrantedPermissions().add(Permission.EXPORT);
        users.saveAndFlush(renamed);
        alreadyInactive.setActive(false);
        users.saveAndFlush(alreadyInactive);
        String oldHash = renamed.getPasswordHash(), adminHash = admin.getPasswordHash();
        AppUser anotherAdmin = user("otheradmin");
        anotherAdmin.getGrantedPermissions().add(Permission.ADMIN_PANEL);
        users.saveAndFlush(anotherAdmin);
        Long reportId = transaction.execute(status -> {
            Report report = new Report();
            report.setCaller(renamed);
            report.setCallerTeam(home);
            report.setTargetTeam(home);
            report.setTargetUser(outside);
            report.setContactMethod(ContactMethod.PHONE);
            report.setContactAt(LocalDateTime.now());
            report.setSubject("Existing report");
            report.setReason(context.getBean(ReasonRepository.class).findAll().get(0));
            report.setStatus(Status.APPROVED);
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            reports.saveAndFlush(report);
            ReportEvent event = new ReportEvent();
            event.setReport(report);
            event.setActor(renamed);
            event.setEventType(EventType.APPROVED);
            event.setCreatedAt(LocalDateTime.now());
            events.saveAndFlush(event);
            return report.getId();
        });

        var result = replace(true, new String[]{"New Team", "Updated name", "reza.contact", "TEAM_MANAGER", "", "u120", ""});
        assertEquals(new UserRosterService.ReplacementResult(0, 1, 1, List.of()), result);
        AppUser updated = users.findByUsername("reza.contact").orElseThrow();
        assertEquals(renamed.getId(), updated.getId());
        assertEquals(oldHash, updated.getPasswordHash());
        assertTrue(updated.isMustChangePassword());
        assertTrue(updated.has(Permission.EXPORT));
        assertTrue(users.findByUsername("u120").isEmpty());
        assertFalse(users.findById(outside.getId()).orElseThrow().isActive());
        assertFalse(users.findById(alreadyInactive.getId()).orElseThrow().isActive());
        UserFilter activeFilter = new UserFilter();
        activeFilter.setActive(true);
        assertEquals(Set.of(admin.getId(), anotherAdmin.getId(), updated.getId()),
                new HashSet<>(users.findAll(activeFilter.toSpec()).stream().map(AppUser::getId).toList()));
        activeFilter.setActive(false);
        assertEquals(Set.of(outside.getId(), alreadyInactive.getId()),
                new HashSet<>(users.findAll(activeFilter.toSpec()).stream().map(AppUser::getId).toList()));
        assertEquals(adminHash, users.findById(admin.getId()).orElseThrow().getPasswordHash());
        assertTrue(users.findById(admin.getId()).orElseThrow().isActive());
        assertTrue(users.findById(anotherAdmin.getId()).orElseThrow().isActive());
        assertTrue(users.findById(anotherAdmin.getId()).orElseThrow().has(Permission.ADMIN_PANEL));
        assertEquals(renamed.getId(), reports.findById(reportId).orElseThrow().getCaller().getId());
        assertEquals(outside.getId(), reports.findById(reportId).orElseThrow().getTargetUser().getId());
        assertEquals(renamed.getId(), events.findAll().get(0).getActor().getId());
    }

    @Test
    void explicitPasswordsApplyFlagAndNewUsersUseDefaultPassword() throws Exception {
        AppUser existing = user("existing");
        var data = workbook(false,
                new String[]{home.getName(), "Existing", "existing", "USER", "New@123"},
                new String[]{home.getName(), "New", "newperson", "USER", ""});
        var result = roster.replaceUsers(data, admin.getId(), true);
        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertTrue(result.errors().isEmpty());
        AppUser changed = users.findById(existing.getId()).orElseThrow();
        assertTrue(encoder.matches("New@123", changed.getPasswordHash()));
        assertTrue(changed.isMustChangePassword());
        AppUser created = users.findByUsername("newperson").orElseThrow();
        assertTrue(encoder.matches("Peygir@123", created.getPasswordHash()));
        assertTrue(created.isMustChangePassword());
        replace(false, new String[]{home.getName(), "Third", "third", "USER", ""});
        assertFalse(users.findByUsername("third").orElseThrow().isMustChangePassword());
    }

    @Test
    void oneInvalidRowRejectsEverythingIncludingNewTeamsRenamesAndDeactivation() throws Exception {
        AppUser existing = user("u120"), outside = user("outside");
        long teamCount = teams.count(), userCount = users.count();
        var result = replace(true,
                new String[]{"Must not be created", "Changed", "newcontact", "USER", "", "u120", "New supervision"},
                new String[]{home.getName(), "x".repeat(151), "another", "USER", "", "", ""});
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.deactivated());
        assertFalse(result.errors().isEmpty());
        assertEquals(teamCount, teams.count());
        assertEquals(userCount, users.count());
        assertEquals(existing.getUsername(), users.findById(existing.getId()).orElseThrow().getUsername());
        assertTrue(users.findById(outside.getId()).orElseThrow().isActive());
    }

    @Test
    void rejectsConflictingOldNewIdentitiesSwapsAndDuplicateMappings() throws Exception {
        AppUser first = user("first"), second = user("second");
        var conflict = replace(true, new String[]{home.getName(), "Changed", "second", "USER", "", "first", ""});
        assertFalse(conflict.errors().isEmpty());
        var swap = replace(true,
                new String[]{home.getName(), "First", "second", "USER", "", "first", ""},
                new String[]{home.getName(), "Second", "first", "USER", "", "second", ""});
        assertFalse(swap.errors().isEmpty());
        var duplicate = replace(true,
                new String[]{home.getName(), "First", "changed", "USER", "", "first", ""},
                new String[]{home.getName(), "First again", "first", "USER", "", "", ""});
        assertFalse(duplicate.errors().isEmpty());
        var caseDuplicate = replace(false,
                new String[]{home.getName(), "New one", "CONTACT", "USER", ""},
                new String[]{home.getName(), "New two", "contact", "USER", ""});
        assertFalse(caseDuplicate.errors().isEmpty());
        assertEquals("first", users.findById(first.getId()).orElseThrow().getUsername());
        assertEquals("second", users.findById(second.getId()).orElseThrow().getUsername());
        assertTrue(users.findById(first.getId()).orElseThrow().isActive());
        assertTrue(users.findById(second.getId()).orElseThrow().isActive());
    }

    @Test
    void appliesMultipleSupervisedTeamsPreservesMissingColumnAndClearsExplicitEmptyCell() throws Exception {
        AppUser person = user("observer");
        replace(true, new String[]{home.getName(), person.getFullName(), "observer", "USER", "", "", "Alpha | Beta؛Alpha"});
        AppUser changed = users.findById(person.getId()).orElseThrow();
        assertEquals(Set.of("Alpha", "Beta"), changed.getSupervisedTeams().stream().map(Team::getName).collect(Collectors.toSet()));
        assertEquals(home.getId(), changed.getTeam().getId());
        replace(false, new String[]{home.getName(), person.getFullName(), "observer", "USER", ""});
        assertEquals(2, users.findById(person.getId()).orElseThrow().getSupervisedTeams().size());
        replace(true, new String[]{home.getName(), person.getFullName(), "observer", "USER", "", "", ""});
        assertTrue(users.findById(person.getId()).orElseThrow().getSupervisedTeams().isEmpty());
    }

    @Test
    void protectedAdminRowCannotChangeIdentityRolesOrPassword() throws Exception {
        AppUser ordinary = user("ordinary");
        String[] valid = {home.getName(), ordinary.getFullName(), "ordinary", "USER", "", "", ""};
        for (String[] malicious : List.of(
                new String[]{admin.getTeam().getName(), "Changed", "admin", "ADMIN", "", "", ""},
                new String[]{admin.getTeam().getName(), admin.getFullName(), "admin", "USER", "", "", ""},
                new String[]{admin.getTeam().getName(), admin.getFullName(), "admin", "ADMIN", "Reset@123", "", ""},
                new String[]{admin.getTeam().getName(), admin.getFullName(), "renamedadmin", "ADMIN", "", "admin", ""})) {
            assertFalse(replace(true, valid, malicious).errors().isEmpty());
            AppUser unchanged = users.findById(admin.getId()).orElseThrow();
            assertEquals("admin", unchanged.getUsername());
            assertEquals(admin.getPasswordHash(), unchanged.getPasswordHash());
            assertTrue(unchanged.has(Permission.ADMIN_PANEL));
        }
        var allowed = replace(true, valid, new String[]{admin.getTeam().getName(), admin.getFullName(),
                "admin", "ADMIN", "", "", ""});
        assertTrue(allowed.errors().isEmpty());
        assertEquals(1, allowed.updated(), "The preserved admin row is not counted as an updated roster member");
    }

    @Test
    void rejectsEmptyMalformedAndWrongHeaderFilesWithoutDeactivatingUsers() throws Exception {
        AppUser existing = user("existing");
        assertThrows(BusinessException.class, () -> replace(false));
        assertThrows(BusinessException.class, () -> roster.replaceUsers(new ByteArrayInputStream(new byte[]{1, 2, 3}), admin.getId(), false));
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet().createRow(0).createCell(0).setCellValue("Unrelated contact workbook");
            wb.write(out);
            assertThrows(BusinessException.class, () -> roster.replaceUsers(new ByteArrayInputStream(out.toByteArray()), admin.getId(), false));
        }
        assertTrue(users.findById(existing.getId()).orElseThrow().isActive());
    }

    @Test
    void rejectsWrongFifthHeaderBeforeReadingOldUsernameAsPassword() throws Exception {
        AppUser existing = user("existing"), outside = user("outside");
        String originalHash = existing.getPasswordHash();
        long originalUserCount = users.count(), originalTeamCount = teams.count();
        var in = workbookWithHeaders(new String[]{"تیم", "نام", "نام کاربری", "نقش", "نام کاربری قبلی"},
                new String[]{home.getName(), "Changed", "existing", "USER", "oldusername"},
                new String[]{"Must not be created", "New", "newperson", "USER", "anotheroldname"});
        assertThrows(BusinessException.class, () -> roster.replaceUsers(in, admin.getId(), true));
        AppUser unchanged = users.findById(existing.getId()).orElseThrow();
        assertEquals(existing.getFullName(), unchanged.getFullName());
        assertEquals(originalHash, unchanged.getPasswordHash());
        assertFalse(unchanged.isMustChangePassword());
        assertTrue(users.findById(outside.getId()).orElseThrow().isActive());
        assertEquals(originalUserCount, users.count());
        assertEquals(originalTeamCount, teams.count());
    }

    @Test
    void acceptsStandardImportTemplateHeadersAndOptionalBlankPasswords() throws Exception {
        AppUser existing = user("existing");
        String originalHash = existing.getPasswordHash();
        var in = workbookWithHeaders(new String[]{"تیم", "نام و نام خانوادگی", "نام کاربری",
                        "نقش (چند نقش با ویرگول)", "رمز اولیه (اختیاری)"},
                new String[]{home.getName(), "Updated", "existing", "USER", ""},
                new String[]{home.getName(), "New", "newperson", "USER", ""});
        var result = roster.replaceUsers(in, admin.getId(), false);
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.updated());
        assertEquals(1, result.created());
        assertEquals(originalHash, users.findById(existing.getId()).orElseThrow().getPasswordHash());
        assertTrue(encoder.matches("Peygir@123", users.findByUsername("newperson").orElseThrow().getPasswordHash()));
        var legacy = workbookWithHeaders(new String[]{"نام تیم", "نام", "نام‌کاربری", "نقش", "رمز اولیه"},
                new String[]{home.getName(), "Updated again", "existing", "USER", ""});
        assertTrue(roster.replaceUsers(legacy, admin.getId(), false).errors().isEmpty());
    }

    @Test
    void missingOldUsernameFallsBackToExactNewUsernameWithoutGuessingByName() throws Exception {
        AppUser existing = user("existing");
        existing.setActive(false);
        users.saveAndFlush(existing);
        var result = replace(true,
                new String[]{home.getName(), existing.getFullName(), "existing", "USER", "", "notpresent", ""},
                new String[]{home.getName(), existing.getFullName(), "different.contact", "USER", "", "alsomissing", ""});
        assertEquals(1, result.updated());
        assertEquals(1, result.created());
        assertEquals(existing.getId(), users.findByUsername("existing").orElseThrow().getId());
        assertTrue(users.findById(existing.getId()).orElseThrow().isActive());
        assertNotEquals(existing.getId(), users.findByUsername("different.contact").orElseThrow().getId());
    }

    @Test
    void validatesFieldLengthsRolesAndPasswordBytesBeforeWriting() throws Exception {
        AppUser existing = user("existing");
        var result = replace(true,
                new String[]{"x".repeat(121), "Name", "x".repeat(81), "UNKNOWN", "short", "y".repeat(81), "z".repeat(121)},
                new String[]{home.getName(), "Name", "newone", "USER", "ا".repeat(40), "", ""});
        assertTrue(result.errors().size() >= 7);
        assertEquals(0, result.created());
        assertTrue(users.findById(existing.getId()).orElseThrow().isActive());
        assertFalse(users.existsByUsername("newone"));
    }

    @Test
    void nonAdminCannotReplaceRoster() throws Exception {
        AppUser person = user("person");
        var in = workbook(false, new String[]{home.getName(), "New", "new", "USER", ""});
        assertThrows(BusinessException.class, () -> roster.replaceUsers(in, person.getId(), false));
        assertTrue(users.findById(person.getId()).orElseThrow().isActive());
        assertFalse(users.existsByUsername("new"));
    }
}
