package org.motechproject.tasks.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.util.Order;
import org.motechproject.tasks.domain.mds.task.Task;
import org.motechproject.tasks.domain.mds.task.TaskActivity;
import org.motechproject.tasks.domain.mds.task.TaskActivityType;
import org.motechproject.tasks.exception.TaskHandlerException;
import org.motechproject.tasks.repository.TaskActivitiesDataService;
import org.motechproject.tasks.service.TaskActivityService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.exception.ExceptionUtils.getStackTrace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.motechproject.tasks.domain.mds.task.TaskActivityType.ERROR;
import static org.motechproject.tasks.domain.mds.task.TaskActivityType.SUCCESS;
import static org.motechproject.tasks.domain.mds.task.TaskActivityType.WARNING;
import static org.motechproject.tasks.constants.TaskFailureCause.TRIGGER;

public class TaskActivityServiceImplTest {

    private static final Long TASK_ID = 12345l;
    private static final List<String> ERROR_FIELD = asList("phone");

    private List<TaskActivity> activities;

    @Mock
    TaskActivitiesDataService taskActivitiesDataService;

    TaskActivityService activityService;

    Task task;

    @Before
    public void setup() throws Exception {
        initMocks(this);

        activityService = new TaskActivityServiceImpl(taskActivitiesDataService);
        activities = createTaskActivities();

        task = new Task();
        task.setId(TASK_ID);
        task.setFailuresInRow(0);
    }

    @Test
    public void shouldAddErrorActivityWithTaskException() {
        String messageKey = "error.notFoundTrigger";
        TaskHandlerException exception = new TaskHandlerException(TRIGGER, messageKey, ERROR_FIELD.get(0));
        Map<String, Object> errorParameters = new HashMap<>();
        errorParameters.put("errorKey", "errorValue");

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);

        activityService.addError(task, exception, errorParameters);

        verify(taskActivitiesDataService).create(captor.capture());

        assertActivity(messageKey, ERROR_FIELD, TASK_ID, TaskActivityType.ERROR, getStackTrace(exception), errorParameters, captor.getValue());
    }

    @Test
    public void shouldAddTaskSuccessActivity() {
        String messageKey = "task.success.ok";

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);

        activityService.addSuccess(task);

        verify(taskActivitiesDataService).create(captor.capture());

        assertActivity(messageKey, Collections.<String>emptyList(), TASK_ID,
                TaskActivityType.SUCCESS, null, null, captor.getValue());
    }

    @Test
    public void shouldAddTaskWarningActivity() {
        String messageKey = "task.warning.taskDisabled";

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);

        activityService.addWarning(task);

        verify(taskActivitiesDataService).create(captor.capture());

        assertActivity(messageKey, Collections.<String>emptyList(), TASK_ID,
                TaskActivityType.WARNING, null, null, captor.getValue());
    }

    @Test
    public void shouldAddTaskWarningActivityWithGivenKeyAndField() {
        String messageKey = "warning.manipulation";

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);

        activityService.addWarning(task, messageKey, ERROR_FIELD.get(0));

        verify(taskActivitiesDataService).create(captor.capture());

        assertActivity(messageKey, ERROR_FIELD, TASK_ID, TaskActivityType.WARNING, null, null, captor.getValue());
    }

    @Test
    public void shouldAddTaskWarningActivityWithGivenException() {
        TaskHandlerException exception = new TaskHandlerException(TRIGGER, "trigger.exception", new TaskHandlerException(TRIGGER, "task.exception"));
        String messageKey = "warning.manipulation";

        ArgumentCaptor<TaskActivity> captor = ArgumentCaptor.forClass(TaskActivity.class);

        activityService.addWarning(task, messageKey, ERROR_FIELD.get(0), exception);

        verify(taskActivitiesDataService).create(captor.capture());

        assertActivity(messageKey, ERROR_FIELD, TASK_ID, TaskActivityType.WARNING, getStackTrace(exception.getCause()), null, captor.getValue());
    }

    @Test
    public void shouldDeleteAllTaskActivitiesForGivenTask() {
        when(taskActivitiesDataService.byTask(TASK_ID)).thenReturn(activities);

        activityService.deleteActivitiesForTask(TASK_ID);

        verify(taskActivitiesDataService, times(activities.size())).delete(any(TaskActivity.class));
    }

    @Test
    public void shouldNotRemoveAnyActivitiesWhenTaskHasNotActivities() {
        when(taskActivitiesDataService.byTask(TASK_ID)).thenReturn(new ArrayList<TaskActivity>());

        activityService.deleteActivitiesForTask(TASK_ID);

        verify(taskActivitiesDataService, never()).delete(any(TaskActivity.class));
    }

    @Test
    public void shouldReturnPaginatedActivitiesForGivenTask() {
        Set<TaskActivityType> types = new HashSet<>();
        types.addAll(Arrays.asList(TaskActivityType.values()));
        QueryParams queryParams = new QueryParams((Order) null);
        when(taskActivitiesDataService.byTaskAndActivityTypes(TASK_ID, types, queryParams)).thenReturn(activities);

        List<TaskActivity> actual = activityService.getTaskActivities(TASK_ID, types, queryParams);

        assertNotNull(actual);
        assertEquals(activities, actual);
    }

    private void assertActivity(String messageKey, List<String> field, Long taskId, TaskActivityType activityType,
                                String stackTraceElement, Map<String, Object> errorParams, TaskActivity activity) {
        assertNotNull(activity);

        assertEquals(messageKey, activity.getMessage());
        assertEquals(taskId, activity.getTask());
        assertEquals(activityType, activity.getActivityType());
        assertEquals(stackTraceElement, activity.getStackTraceElement());
        assertEquals(errorParams, activity.getParameters());

        assertEquals(field, activity.getFields());
    }

    private List<TaskActivity> createTaskActivities() {
        List<TaskActivity> messages = new ArrayList<>();
        messages.add(createError());
        messages.add(createError());
        messages.add(createSuccess());
        messages.add(createError());
        messages.add(createError());
        messages.add(createWarning());
        messages.add(createSuccess());
        messages.add(createError());
        messages.add(createError());
        messages.add(createError());
        messages.add(createError());

        return messages;
    }

    private TaskActivity createError() {
        return new TaskActivity(ERROR.getValue(), ERROR_FIELD, TASK_ID, ERROR);
    }

    private TaskActivity createSuccess() {
        return new TaskActivity(SUCCESS.getValue(), TASK_ID, SUCCESS);
    }

    private TaskActivity createWarning() {
        return new TaskActivity(WARNING.getValue(), TASK_ID, WARNING);
    }
}
