package org.motechproject.tasks.web;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.motechproject.config.SettingsFacade;
import org.motechproject.tasks.web.domain.SettingsDto;
import org.osgi.framework.BundleException;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(PowerMockRunner.class)
public class SettingsControllerTest {
    private static final String TASK_POSSIBLE_ERRORS_KEY = "task.possible.errors";
    private static final String TASK_POSSIBLE_ERRORS_VALUE = "123";

    @Mock
    private SettingsFacade settingsFacade;

    @InjectMocks
    private SettingsController controller = new SettingsController();

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testGetSettings() {
        when(settingsFacade.getProperty(TASK_POSSIBLE_ERRORS_KEY)).thenReturn(TASK_POSSIBLE_ERRORS_VALUE);

        SettingsDto dto = controller.getSettings();

        verify(settingsFacade, times(1)).getProperty(anyString());
        assertEquals(TASK_POSSIBLE_ERRORS_VALUE, dto.getTaskPossibleErrors());
    }

    @Test
    public void testSaveSettings() throws BundleException {
        SettingsDto dto = new SettingsDto();
        dto.setTaskPossibleErrors(TASK_POSSIBLE_ERRORS_VALUE);

        controller.saveSettings(dto);

        verify(settingsFacade, times(1)).setProperty(anyString(), anyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveSettingsEmptyProperty() throws BundleException {
        SettingsDto dto = new SettingsDto();
        dto.setTaskPossibleErrors(null);
        controller.saveSettings(dto);

        verify(settingsFacade, never()).setProperty(anyString(), anyString());
    }

}
