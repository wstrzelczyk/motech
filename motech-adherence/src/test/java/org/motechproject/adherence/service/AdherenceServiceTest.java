package org.motechproject.adherence.service;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.motechproject.adherence.dao.AllAdherenceLogs;
import org.motechproject.adherence.domain.AdherenceLog;
import org.motechproject.adherence.domain.ErrorFunction;
import org.motechproject.util.DateUtil;

import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AdherenceServiceTest extends BaseUnitTest {

    @Mock
    private AllAdherenceLogs allAdherenceLogs;
    private AdherenceService adherenceService;
    private String externalId;

    @Before
    public void setUp() {
        initMocks(this);
        adherenceService = new AdherenceService(allAdherenceLogs);
        externalId = "externalId";
    }

    @Test
    public void shouldStartRecordingAdherence() {
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(null);

        adherenceService.recordDoseTaken(externalId, true, new ErrorFunction(1, 1));
        ArgumentCaptor<AdherenceLog> logCapture = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs).insert(logCapture.capture());
        assertEquals(1, logCapture.getValue().getDosesTaken());
        assertEquals(1, logCapture.getValue().getTotalDoses());
    }

    @Test
    public void shouldRecordDoseTaken() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(1);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        adherenceService.recordDoseTaken(externalId, true, new ErrorFunction(1, 1));
        ArgumentCaptor<AdherenceLog> logCapture = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs).insert(logCapture.capture());
        assertEquals(2, logCapture.getValue().getDosesTaken());
        assertEquals(2, logCapture.getValue().getTotalDoses());
    }

    @Test
    public void shouldRecordDoseNotTaken() {
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(null);

        adherenceService.recordDoseTaken(externalId, false, new ErrorFunction(1, 1));
        ArgumentCaptor<AdherenceLog> logCapture = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs).insert(logCapture.capture());
        assertEquals(0, logCapture.getValue().getDosesTaken());
        assertEquals(1, logCapture.getValue().getTotalDoses());
    }

    @Test
    public void shouldCorrectErrorWhenRecordingAdherence() {
        DateTime now = new DateTime(2011, 12, 2, 10, 0, 0, 0);
        mockTime(now);

        AdherenceLog existingLog = AdherenceLog.create(externalId, now.toLocalDate());
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        existingLog.setFromDate(now.toLocalDate().minusDays(2));
        existingLog.setToDate(now.toLocalDate().minusDays(2));

        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        adherenceService.recordDoseTaken(externalId, true, new ErrorFunction(0, 1));
        ArgumentCaptor<AdherenceLog> logCaptor = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs, times(2)).insert(logCaptor.capture());
        List<AdherenceLog> allLogs = logCaptor.getAllValues();
        assertEquals(1, allLogs.get(0).getDosesTaken());
        assertEquals(3, allLogs.get(0).getTotalDoses());
        assertEquals(2, allLogs.get(1).getDosesTaken());
        assertEquals(4, allLogs.get(1).getTotalDoses());
    }

    @Test
    public void shouldRecordAdherenceBetweenARange() {
        LocalDate fromDate = DateUtil.newDate(2011, 12, 1);
        LocalDate toDate = DateUtil.newDate(2011, 12, 31);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(null);

        adherenceService.recordAdherence(externalId, 1, 1, fromDate, toDate, new ErrorFunction(0, 0));
        ArgumentCaptor<AdherenceLog> logCapture = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs).insert(logCapture.capture());
        assertEquals(fromDate, logCapture.getValue().getFromDate());
        assertEquals(toDate, logCapture.getValue().getToDate());
    }

    @Test
    public void shouldCorrectErrorWhenRecordingAdherenceBetweenARange() {
        DateTime now = new DateTime(2011, 12, 2, 10, 0, 0, 0);
        mockTime(now);

        AdherenceLog existingLog = AdherenceLog.create(externalId, now.toLocalDate());
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        existingLog.setFromDate(now.toLocalDate().minusDays(2));
        existingLog.setToDate(now.toLocalDate().minusDays(2));

        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        adherenceService.recordAdherence(externalId, 1, 1, now.toLocalDate(), now.toLocalDate(), new ErrorFunction(0, 1));
        ArgumentCaptor<AdherenceLog> logCaptor = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs, times(2)).insert(logCaptor.capture());
        List<AdherenceLog> allLogs = logCaptor.getAllValues();
        assertEquals(1, allLogs.get(0).getDosesTaken());
        assertEquals(3, allLogs.get(0).getTotalDoses());
        assertEquals(2, allLogs.get(1).getDosesTaken());
        assertEquals(4, allLogs.get(1).getTotalDoses());
    }

    @Test
    public void shouldReportRunningAverageAdherence() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        assertEquals(0.5, adherenceService.getRunningAverageAdherence(externalId));
    }

    @Test
    public void shouldReportRunningAverageAdherenceOnGivenDate() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        LocalDate date = DateUtil.newDate(2011, 12, 1);
        when(allAdherenceLogs.findByDate(externalId, date)).thenReturn(existingLog);

        assertEquals(0.5, adherenceService.getRunningAverageAdherence(externalId, date));
    }

    @Test
    public void shouldReportDeltaAdherence() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        existingLog.setDeltaDosesTaken(1);
        existingLog.setDeltaTotalDoses(4);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        assertEquals(0.25, adherenceService.getDeltaAdherence(externalId));
    }

    @Test
    public void shouldReportDeltaAdherenceOverDateRange() {
        LocalDate today = DateUtil.today();
        AdherenceLog log = AdherenceLog.create(externalId, today);
        log.setDeltaDosesTaken(1);
        log.setDeltaTotalDoses(1);
        AdherenceLog secondLog = AdherenceLog.create(externalId, today);
        secondLog.setDeltaDosesTaken(0);
        secondLog.setDeltaTotalDoses(1);

        LocalDate fromDate = DateUtil.newDate(2011, 12, 1);
        LocalDate toDate = DateUtil.newDate(2011, 12, 31);

        when(allAdherenceLogs.findLogsBetween(externalId, fromDate, toDate)).thenReturn(Arrays.asList(log, secondLog));
        assertEquals(0.5, adherenceService.getDeltaAdherence(externalId, fromDate, toDate));
    }

    @Test
    public void shouldUpdateLatestAdherenceForPositiveChangeInDeltas() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(1);
        existingLog.setTotalDoses(2);
        existingLog.setDeltaDosesTaken(1);
        existingLog.setDeltaTotalDoses(2);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        adherenceService.updateLatestAdherence(externalId, 3, 4);
        ArgumentCaptor<AdherenceLog> logCaptor = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs, times(1)).update(logCaptor.capture());
        AdherenceLog allLog = logCaptor.getValue();
        assertEquals(3, allLog.getDosesTaken());
        assertEquals(4, allLog.getTotalDoses());
        assertEquals(3, allLog.getDosesTaken());
        assertEquals(4, allLog.getTotalDoses());
    }

    @Test
    public void shouldUpdateLatestAdherenceForNegativeChangeInDeltas() {
        LocalDate today = DateUtil.today();
        AdherenceLog existingLog = AdherenceLog.create(externalId, today);
        existingLog.setDosesTaken(4);
        existingLog.setTotalDoses(5);
        existingLog.setDeltaDosesTaken(3);
        existingLog.setDeltaTotalDoses(4);
        when(allAdherenceLogs.findLatestLog(externalId)).thenReturn(existingLog);

        adherenceService.updateLatestAdherence(externalId, 2, 3);
        ArgumentCaptor<AdherenceLog> logCaptor = ArgumentCaptor.forClass(AdherenceLog.class);
        verify(allAdherenceLogs, times(1)).update(logCaptor.capture());
        AdherenceLog allLog = logCaptor.getValue();
        assertEquals(3, allLog.getDosesTaken());
        assertEquals(4, allLog.getTotalDoses());
        assertEquals(2, allLog.getDeltaDosesTaken());
        assertEquals(3, allLog.getDeltaTotalDoses());
    }

    @After
    public void tearDown() {
        resetTime();
    }
}
