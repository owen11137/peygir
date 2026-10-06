package com.novinkish.peygir.service;

import com.novinkish.peygir.PeygirApplication;
import com.novinkish.peygir.domain.*;
import com.novinkish.peygir.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises deletion rules against the migrated database and real transactions. */
class TeamDeletionTest {
    private static ConfigurableApplicationContext context;
    private static AdminService admin;
    private static TeamRepository teams;
    private static UserRepository users;
    private static ReportRepository reports;
    private static ReasonRepository reasons;

    @BeforeAll
    static void start() {
        context = SpringApplication.run(PeygirApplication.class,
                "--server.port=0", "--server.ssl.enabled=false",
                "--spring.datasource.url=jdbc:h2:mem:team-deletion;DB_CLOSE_DELAY=-1",
                "--peygir.seed-demo=false", "--logging.level.root=WARN");
        admin = context.getBean(AdminService.class);
        teams = context.getBean(TeamRepository.class);
        users = context.getBean(UserRepository.class);
        reports = context.getBean(ReportRepository.class);
        reasons = context.getBean(ReasonRepository.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    private Team team() {
        return teams.saveAndFlush(new Team("test-" + UUID.randomUUID()));
    }

    private void report(Team callerTeam, Team targetTeam, Status status) {
        Report report = new Report();
        report.setCaller(users.findByUsername("admin").orElseThrow());
        report.setCallerTeam(callerTeam);
        report.setTargetTeam(targetTeam);
        report.setContactMethod(ContactMethod.PHONE);
        report.setContactAt(LocalDateTime.now());
        report.setSubject("Deletion guard");
        report.setReason(reasons.findAll().get(0));
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reports.saveAndFlush(report);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletesUnusedTeam(boolean active) {
        Team team = team();
        team.setActive(active);
        teams.saveAndFlush(team);
        admin.deleteTeam(team.getId());
        assertFalse(teams.existsById(team.getId()));
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void blocksCallerAndTargetTeamsInEveryReportStatus(Status status) {
        Team caller = team(), target = team();
        report(caller, target, status);
        assertThrows(BusinessException.class, () -> admin.deleteTeam(caller.getId()));
        assertThrows(BusinessException.class, () -> admin.deleteTeam(target.getId()));
        assertTrue(teams.existsById(caller.getId()));
        assertTrue(teams.existsById(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void requiresMovingMembersIncludingInactiveMembers(boolean active) {
        Team team = team();
        String username = "user-" + UUID.randomUUID();
        admin.createUser(username, "Team member", team.getId(), List.of(), "Test@123", false);
        AppUser user = users.findByUsername(username).orElseThrow();
        user.setActive(active);
        users.saveAndFlush(user);
        assertThrows(BusinessException.class, () -> admin.deleteTeam(team.getId()));
        assertTrue(users.existsById(user.getId()));
        user.setTeam(users.findByUsername("admin").orElseThrow().getTeam());
        users.saveAndFlush(user);
        admin.deleteTeam(team.getId());
        assertFalse(teams.existsById(team.getId()));
        assertTrue(users.existsById(user.getId()));
    }

    @Test
    void missingTeamHasBusinessError() {
        assertThrows(BusinessException.class, () -> admin.deleteTeam(Long.MAX_VALUE));
    }

    @Test
    void databaseAlsoProtectsReferencedTeam() {
        Team caller = team(), target = team();
        report(caller, target, Status.CLOSED);
        assertThrows(DataIntegrityViolationException.class, () -> teams.deleteById(target.getId()));
        assertTrue(teams.existsById(target.getId()));
    }
}
