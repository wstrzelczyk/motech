package org.motechproject.tasks.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.motechproject.commons.api.DataProvider;
import org.motechproject.commons.api.TasksEventParser;
import org.motechproject.config.SettingsFacade;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventListener;
import org.motechproject.event.listener.EventListenerRegistryService;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListenerEventProxy;
import org.motechproject.tasks.domain.mds.channel.ActionEvent;
import org.motechproject.tasks.domain.mds.channel.builder.ActionEventBuilder;
import org.motechproject.tasks.domain.mds.channel.ActionParameter;
import org.motechproject.tasks.domain.mds.channel.builder.ActionParameterBuilder;
import org.motechproject.tasks.constants.EventDataKeys;
import org.motechproject.tasks.domain.mds.task.DataSource;
import org.motechproject.tasks.domain.mds.channel.EventParameter;
import org.motechproject.tasks.domain.mds.task.Filter;
import org.motechproject.tasks.domain.mds.task.FilterSet;
import org.motechproject.tasks.domain.mds.task.Lookup;
import org.motechproject.tasks.domain.mds.task.Task;
import org.motechproject.tasks.domain.mds.task.TaskActionInformation;
import org.motechproject.tasks.domain.mds.task.TaskActivity;
import org.motechproject.tasks.domain.mds.task.TaskConfig;
import org.motechproject.tasks.domain.mds.task.TaskTriggerInformation;
import org.motechproject.tasks.domain.mds.channel.TriggerEvent;
import org.motechproject.tasks.exception.ActionNotFoundException;
import org.motechproject.tasks.exception.TaskHandlerException;
import org.motechproject.tasks.service.SampleTasksEventParser;
import org.motechproject.tasks.service.TaskActivityService;
import org.motechproject.tasks.service.TaskService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.motechproject.tasks.constants.EventSubjects.SCHEDULE_REPEATING_JOB;
import static org.motechproject.tasks.constants.EventSubjects.UNSCHEDULE_REPEATING_JOB;
import static org.motechproject.tasks.constants.EventSubjects.createHandlerFailureSubject;
import static org.motechproject.tasks.constants.EventSubjects.createHandlerSuccessSubject;
import static org.motechproject.tasks.constants.TaskFailureCause.ACTION;
import static org.motechproject.tasks.constants.TaskFailureCause.DATA_SOURCE;
import static org.motechproject.tasks.constants.TaskFailureCause.TRIGGER;
import static org.motechproject.tasks.domain.mds.task.OperatorType.CONTAINS;
import static org.motechproject.tasks.domain.mds.task.OperatorType.ENDSWITH;
import static org.motechproject.tasks.domain.mds.task.OperatorType.EQUALS;
import static org.motechproject.tasks.domain.mds.task.OperatorType.EQUALS_IGNORE_CASE;
import static org.motechproject.tasks.domain.mds.task.OperatorType.EQ_NUMBER;
import static org.motechproject.tasks.domain.mds.task.OperatorType.EXIST;
import static org.motechproject.tasks.domain.mds.task.OperatorType.GT;
import static org.motechproject.tasks.domain.mds.task.OperatorType.LT;
import static org.motechproject.tasks.domain.mds.task.OperatorType.STARTSWITH;
import static org.motechproject.tasks.domain.mds.ParameterType.BOOLEAN;
import static org.motechproject.tasks.domain.mds.ParameterType.DATE;
import static org.motechproject.tasks.domain.mds.ParameterType.DOUBLE;
import static org.motechproject.tasks.domain.mds.ParameterType.INTEGER;
import static org.motechproject.tasks.domain.mds.ParameterType.LIST;
import static org.motechproject.tasks.domain.mds.ParameterType.LONG;
import static org.motechproject.tasks.domain.mds.ParameterType.MAP;
import static org.motechproject.tasks.domain.mds.ParameterType.TEXTAREA;
import static org.motechproject.tasks.domain.mds.ParameterType.TIME;
import static org.motechproject.tasks.domain.mds.ParameterType.UNICODE;
import static org.motechproject.tasks.domain.mds.task.TaskActivityType.ERROR;
import static org.springframework.aop.support.AopUtils.getTargetClass;
import static org.springframework.util.ReflectionUtils.findMethod;

public class TaskTriggerHandlerTest {
    private static final String TRIGGER_SUBJECT = "APPOINTMENT_CREATE_EVENT_SUBJECT";
    private static final String ACTION_SUBJECT = "SEND_SMS";
    private static final String TASK_DATA_PROVIDER_NAME = "12345L";

    public class TestObjectField {
        private int id = 6789;

        public int getId() {
            return id;
        }
    }

    public class TestObject {
        private TestObjectField field = new TestObjectField();

        public TestObjectField getField() {
            return field;
        }
    }

    public class TestService {
        public void throwException(Integer phone, String message) throws IllegalAccessException {
            throw new IllegalAccessException();
        }

        public void execute(Integer phone, String message) {

        }
    }

    @Mock
    TaskService taskService;

    @Mock
    TaskActivityService taskActivityService;

    @Mock
    EventListenerRegistryService registryService;

    @Mock
    EventRelay eventRelay;

    @Mock
    SettingsFacade settingsFacade;

    @Mock
    DataProvider dataProvider;

    @Mock
    BundleContext bundleContext;

    @Mock
    ServiceReference serviceReference;

    @Mock
    Exception exception;

    @Spy
    @InjectMocks
    TaskActionExecutor taskActionExecutor = new TaskActionExecutor(taskService, taskActivityService, eventRelay);

    @Captor
    ArgumentCaptor<TaskHandlerException> exceptionCaptor;

    @InjectMocks
    TaskTriggerHandler handler = new TaskTriggerHandler();

    List<Task> tasks = new ArrayList<>(1);
    List<TaskActivity> taskActivities;

    Task task;
    TriggerEvent triggerEvent;
    ActionEvent actionEvent;

    @Before
    public void setup() throws Exception {
        initMocks(this);
        initTask();

        when(taskService.getAllTasks()).thenReturn(tasks);
        when(settingsFacade.getProperty("task.possible.errors")).thenReturn("5");
        when(dataProvider.getName()).thenReturn(TASK_DATA_PROVIDER_NAME);

        // do the initialization, normally called by Spring as @PostConstruct
        handler.init();
        handler.addDataProvider(dataProvider);
        handler.setBundleContext(null);

        verify(taskService).getAllTasks();
        verify(registryService).registerListener(any(EventListener.class), eq(task.getTrigger().getSubject()));
    }

    @Test
    public void shouldNotRegisterHandler() {
        EventListenerRegistryService eventListenerRegistryService = mock(EventListenerRegistryService.class);

        when(taskService.getAllTasks()).thenReturn(new ArrayList<Task>());

        handler.init();
        verify(eventListenerRegistryService, never()).registerListener(any(EventListener.class), anyString());
    }

    @Test
    public void shouldRegisterHandlerForSubject() {
        String subject = "org.motechproject.messagecampaign.campaign-completed";

        handler.registerHandlerFor(subject);
        ArgumentCaptor<EventListener> captor = ArgumentCaptor.forClass(EventListener.class);

        verify(registryService).registerListener(captor.capture(), eq(subject));

        MotechListenerEventProxy proxy = (MotechListenerEventProxy) captor.getValue();

        assertEquals("taskTriggerHandler", proxy.getIdentifier());
        assertEquals(handler, proxy.getBean());
        assertEquals(findMethod(getTargetClass(handler), "handle", MotechEvent.class), proxy.getMethod());
    }

    @Test
    public void shouldRegisterRetryHandlerForSubject() {
        String subject = "org.motechproject.messagecampaign.campaign-completed";

        handler.registerHandlerFor(subject, true);
        ArgumentCaptor<EventListener> captor = ArgumentCaptor.forClass(EventListener.class);

        verify(registryService).registerListener(captor.capture(), eq(subject));

        MotechListenerEventProxy proxy = (MotechListenerEventProxy) captor.getValue();

        assertEquals("taskTriggerHandler", proxy.getIdentifier());
        assertEquals(handler, proxy.getBean());
        assertEquals(findMethod(getTargetClass(handler), "handleRetry", MotechEvent.class), proxy.getMethod());
    }

    @Test
    public void shouldRegisterHandlerOneTimeForSameSubjects() {
        String subject = "org.motechproject.messagecampaign.campaign-completed";
        Method method = findMethod(getTargetClass(handler), "handle", MotechEvent.class);

        Set<EventListener> listeners = new HashSet<>();
        listeners.add(new MotechListenerEventProxy("taskTriggerHandler", this, method));

        when(registryService.getListeners(subject)).thenReturn(listeners);

        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);
        handler.registerHandlerFor(subject);

        verify(registryService, never()).registerListener(any(EventListener.class), eq(subject));
    }

    @Test
    public void shouldNotSendEventWhenActionNotFound() throws Exception {
        setTriggerEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenThrow(new ActionNotFoundException(""));

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.actionNotFound", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventWhenActionEventParameterNotContainValue() throws Exception {
        setTriggerEvent();
        setActionEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().remove("phone");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.taskActionNotContainsField", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventWhenActionEventParameterHasNotValue() throws Exception {
        setTriggerEvent();
        setActionEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("phone", null);

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.templateNull", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToInteger() throws Exception {
        setTriggerEvent();
        setActionEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("phone", "1234   d");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToInteger", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToLong() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setLongField();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("long", "1234   d");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToLong", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToDouble() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setDoubleField();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("double", "1234   d");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToDouble", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToBoolean() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setBooleanField();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("boolean", "abc");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToBoolean", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToTime() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTimeField();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("time", "234543fgf");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToTime", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfActionEventParameterCanNotBeConvertedToDate() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setDateField();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("date", "234543fgf");

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("task.error.convertToDate", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldSendEventAndConverseDateWithAndWithoutManipulation() throws Exception {
        setTriggerEvent();
        setActionEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("date1", "2012-12-21 21:21 +0100");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Date1").setKey("date1")
                .setType(DATE).build(), true);
        task.getActions().get(0).getValues().put("date2", "{{trigger.startDate?datetime(yyyyy.MMMMM.dd GGG hh:mm aaa)}}");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Date2").setKey("date2")
                .setType(UNICODE).build(), true);

        handler.handle(createEvent());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addSuccess(eq(task));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());

        assertEquals(createHandlerSuccessSubject(task.getName()), captorEvent.getValue().getSubject());

        List<MotechEvent> events = captorEvent.getAllValues();

        assertEquals(asList(ACTION_SUBJECT, createHandlerSuccessSubject(task.getName())),
                extract(events, on(MotechEvent.class).getSubject()));

        MotechEvent motechEvent = (MotechEvent) CollectionUtils.find(events, new Predicate() {
            @Override
            public boolean evaluate(Object object) {
                return object instanceof MotechEvent && ((MotechEvent) object).getSubject().equalsIgnoreCase(ACTION_SUBJECT);
            }
        });

        assertEquals(ACTION_SUBJECT, motechEvent.getSubject());

        Map<String, Object> motechEventParameters = motechEvent.getParameters();

        assertNotNull(motechEventParameters);

        assertEquals(task.getActions().get(0).getValues().get("phone"), motechEventParameters.get("phone").toString());
        assertEquals(4, motechEventParameters.size());
        assertNotNull(motechEventParameters.get("date1"));
        assertNotNull(motechEventParameters.get("date2"));
    }

    @Test
    public void shouldDisableTaskWhenNumberPossibleErrorsIsExceeded() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        task.getActions().get(0).getValues().put("message", null);

        assertTrue(task.isEnabled());

        handler.handle(createEvent());

        assertEquals(5, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));
        verify(taskService).save(task);
        verify(taskActivityService).addWarning(task);

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        List<MotechEvent> capturedEvents = captorEvent.getAllValues();

        assertEquals(asList("org.motechproject.message", createHandlerFailureSubject(task.getName(), TRIGGER)),
                extract(capturedEvents, on(MotechEvent.class).getSubject()));

        assertFalse(task.isEnabled());
        assertEquals("task.error.templateNull", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldDisableTaskWhenActionDoesNotFindDataSource_WithFailIfDataNotFoundSelected() throws Exception {
        Map<String , DataProvider> providers = new HashMap<>();
        DataProvider provider = mock(DataProvider.class);
        Map<String, String> lookup = new HashMap<>();
        lookup.put("patientId", "123");
        when(provider.lookup("Patient", "", lookup)).thenReturn(null);
        providers.put(TASK_DATA_PROVIDER_NAME, provider);
        handler.setDataProviders(providers);

        TriggerEvent trigger = new TriggerEvent();
        trigger.setSubject("trigger");
        List<EventParameter> triggerEventParameters = new ArrayList<>();
        triggerEventParameters.add(new EventParameter("patientId", "123"));
        trigger.setEventParameters(triggerEventParameters);

        ActionEvent action = new ActionEventBuilder().build();
        action.setSubject("action");
        SortedSet<ActionParameter> actionEventParameters = new TreeSet<>();
        actionEventParameters.add(new ActionParameterBuilder().setDisplayName("Patient ID").setKey("patientId")
                .setType(UNICODE).setOrder(0).build());
        action.setActionParameters(actionEventParameters);

        Task task = new Task();
        task.setName("task");
        task.setTrigger(new TaskTriggerInformation("Trigger", "channel", "module", "0.1", "trigger", "listener"));
        Map<String, String> actionValues = new HashMap<>();
        actionValues.put("patientId", "{{ad.providerId.Patient#1.patientId}}");
        task.addAction(new TaskActionInformation("Action", "channel", "module", "0.1", "action", actionValues));
        task.setId(44l);
        task.setHasRegisteredChannel(true);

        TaskConfig taskConfig = new TaskConfig();
        task.setTaskConfig(taskConfig);
        taskConfig.add(new DataSource(TASK_DATA_PROVIDER_NAME, 4L, 1L, "Patient", "provider",
                asList(new Lookup("patientId", "trigger.patientId")), true));

        List<Task> tasks = asList(task);

        when(taskService.findActiveTasksForTriggerSubject("trigger")).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(action);

        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());

        Map<String, Object> param = new HashMap<>(4);
        param.put("patientId", "123");
        handler.handle(new MotechEvent("trigger", param));

        verify(taskService).save(task);
        ArgumentCaptor<Task> taskArgumentCaptor = ArgumentCaptor.forClass(Task.class);
        assertEquals(5, task.getFailuresInRow());
        verify(taskService).save(taskArgumentCaptor.capture());
        Task actualTask = taskArgumentCaptor.getValue();
        assertFalse(actualTask.isEnabled());
    }

    @Test
    public void shouldNotDisableTaskWhenActionDoesNotFindDataSource_WithFailIfDataNotFoundNotSelected() throws Exception {
        Map<String, DataProvider> providers = new HashMap<>();
        DataProvider provider = mock(DataProvider.class);
        Map<String, String> lookup = new HashMap<>();
        lookup.put("patientId", "123");
        when(provider.lookup("Patient", null, lookup)).thenReturn(null);
        providers.put(TASK_DATA_PROVIDER_NAME, provider);
        handler.setDataProviders(providers);

        TriggerEvent trigger = new TriggerEvent();
        trigger.setSubject("trigger");
        List<EventParameter> triggerEventParameters = new ArrayList<>();
        triggerEventParameters.add(new EventParameter("patientId", "123"));
        trigger.setEventParameters(triggerEventParameters);

        ActionEvent action = new ActionEventBuilder().build();
        action.setSubject("action");
        SortedSet<ActionParameter> actionEventParameters = new TreeSet<>();
        actionEventParameters.add(new ActionParameterBuilder().setDisplayName("Patient ID")
                .setKey("patientId").setType(UNICODE).setOrder(0).build());
        action.setActionParameters(actionEventParameters);

        Task task = new Task();
        task.setName("task");
        task.setTrigger(new TaskTriggerInformation("Trigger", "channel", "module", "0.1", "trigger", "listener"));
        Map<String, String> actionValues = new HashMap<>();
        actionValues.put("patientId", "{{ad.12345.Patient#1.patientId}}");
        task.addAction(new TaskActionInformation("Action", "channel", "module", "0.1", "action", actionValues));
        task.setId(7l);
        task.setHasRegisteredChannel(true);

        TaskConfig taskConfig = new TaskConfig();
        task.setTaskConfig(taskConfig);
        taskConfig.add(new DataSource(TASK_DATA_PROVIDER_NAME, 3L, 1L, "Patient", "provider", asList(new Lookup("patientId", "trigger.patientId")), false));

        List<Task> tasks = asList(task);

        when(taskService.findActiveTasksForTriggerSubject("trigger")).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(action);

        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());

        Map<String, Object> param = new HashMap<>(4);
        param.put("patientId", "123");
        handler.handle(new MotechEvent("trigger", param));

        assertEquals(0, task.getFailuresInRow());
        verify(taskService).save(task);
        verify(taskActivityService).addSuccess(task);
    }

    @Test
    public void shouldDisableTaskWhenFilterDoesNotFindDataSource_WithFailIfDataNotFoundSelected() throws Exception {
        Map<String, DataProvider> providers = new HashMap<>();
        DataProvider provider = mock(DataProvider.class);
        Map<String, String> lookup = new HashMap<>();
        lookup.put("patientId", "123");
        when(provider.lookup("Patient", null, lookup)).thenReturn(null);
        providers.put(TASK_DATA_PROVIDER_NAME, provider);
        handler.setDataProviders(providers);

        TriggerEvent trigger = new TriggerEvent();
        trigger.setSubject("trigger");
        List<EventParameter> triggerEventParameters = new ArrayList<>();
        triggerEventParameters.add(new EventParameter("patientId", "123"));
        trigger.setEventParameters(triggerEventParameters);

        Task task = new Task();
        task.setName("task");
        task.setId(77l);
        task.setTrigger(new TaskTriggerInformation("Trigger", "channel", "module", "0.1", "trigger", "listener"));
        task.setHasRegisteredChannel(true);
        task.setActions(Collections.<TaskActionInformation>emptyList());

        TaskConfig taskConfig = new TaskConfig();
        task.setTaskConfig(taskConfig);
        taskConfig.add(new DataSource(TASK_DATA_PROVIDER_NAME, 4L, 1L, "Patient", "provider",
                asList(new Lookup("patientId", "trigger.patientId")), true));
        taskConfig.add(new FilterSet(asList(new Filter("Patient ID", "ad.12345.Patient#1.patientId", INTEGER, false, EXIST.getValue(), ""))));

        List<Task> tasks = asList(task);

        when(taskService.findActiveTasksForTriggerSubject("trigger")).thenReturn(tasks);

        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());

        Map<String, Object> param = new HashMap<>(4);
        param.put("patientId", "123");
        handler.handle(new MotechEvent("trigger", param));

        ArgumentCaptor<Task> taskArgumentCaptor = ArgumentCaptor.forClass(Task.class);
        assertEquals(5, task.getFailuresInRow());
        verify(taskService).save(task);
        verify(taskService).save(taskArgumentCaptor.capture());
        Task actualTask = taskArgumentCaptor.getValue();
        assertFalse(actualTask.isEnabled());
    }

    @Test
    public void shouldNotDisableTaskWhenFilterDoesNotFindDataSource_WithFailIfDataNotFoundNotSelected() throws Exception {
        Map<String , DataProvider> providers = new HashMap<>();
        DataProvider provider = mock(DataProvider.class);
        Map<String, String> lookup = new HashMap<>();
        lookup.put("patientId", "123");
        when(provider.lookup("Patient", null, lookup)).thenReturn(null);
        providers.put(TASK_DATA_PROVIDER_NAME, provider);
        handler.setDataProviders(providers);

        TriggerEvent trigger = new TriggerEvent();
        trigger.setSubject("trigger");
        List<EventParameter> triggerEventParameters = new ArrayList<>();
        triggerEventParameters.add(new EventParameter("patientId", "123"));
        trigger.setEventParameters(triggerEventParameters);

        Task task = new Task();
        task.setName("task");
        task.setId(44l);
        task.setTrigger(new TaskTriggerInformation("Trigger", "channel", "module", "0.1", "trigger", "listener"));
        task.setHasRegisteredChannel(true);
        task.setActions(Collections.<TaskActionInformation>emptyList());

        TaskConfig taskConfig = new TaskConfig();
        task.setTaskConfig(taskConfig);
        taskConfig.add(new DataSource(TASK_DATA_PROVIDER_NAME, 4L, 1L, "Patient", "provider",
                asList(new Lookup("patientId", "trigger.patientId")), false));
        taskConfig.add(new FilterSet(asList(new Filter("Patient ID", "ad.12345.Patient#1.patientId", INTEGER, false, EXIST.getValue(), ""))));

        List<Task> tasks = asList(task);

        when(taskService.findActiveTasksForTriggerSubject("trigger")).thenReturn(tasks);

        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());

        Map<String, Object> param = new HashMap<>(4);
        param.put("patientId", "123");
        handler.handle(new MotechEvent("trigger", param));

        assertEquals(0, task.getFailuresInRow());
        verify(taskService).save(task);
        verify(taskActivityService).addSuccess(task);
    }

    @Test
    public void shouldNotSendEventIfDataProvidersListIsNull() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());
        setAdditionalData(true);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);

        assertTrue(task.isEnabled());

        handler.setDataProviders(null);
        handler.handle(createEvent());

        assertEquals(5, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        verify(dataProvider, never()).supports(anyString());
        verify(dataProvider, never()).lookup(anyString(), anyString(), anyMap());
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        List<MotechEvent> capturedEvents = captorEvent.getAllValues();

        assertEquals(asList("org.motechproject.message", createHandlerFailureSubject(task.getName(), DATA_SOURCE)),
                extract(capturedEvents, on(MotechEvent.class).getSubject()));

        assertFalse(task.isEnabled());
        assertEquals("task.error.notFoundDataProvider", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfDataProvidersListIsEmpty() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());
        setAdditionalData(true);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);

        assertTrue(task.isEnabled());

        handler.setDataProviders(new HashMap<String, DataProvider>());
        handler.handle(createEvent());

        assertEquals(5, task.getFailuresInRow());
        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        verify(dataProvider, never()).supports(anyString());
        verify(dataProvider, never()).lookup(anyString(), anyString(), anyMap());
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        List<MotechEvent> capturedEvents = captorEvent.getAllValues();

        assertEquals(asList("org.motechproject.message", createHandlerFailureSubject(task.getName(), DATA_SOURCE)),
                extract(capturedEvents, on(MotechEvent.class).getSubject()));

        assertFalse(task.isEnabled());
        assertEquals("task.error.notFoundDataProvider", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfDataProviderNotFoundObject() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());
        setAdditionalData(true);

        Map<String, String> lookupFields = new HashMap<>();
        lookupFields.put("id", "123456789");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        when(dataProvider.getName()).thenReturn("TEST");
        when(dataProvider.supports("TestObjectField")).thenReturn(true);
        when(dataProvider.lookup("TestObjectField", "id", lookupFields)).thenReturn(null);

        assertTrue(task.isEnabled());

        handler.handle(createEvent());

        assertEquals(5, task.getFailuresInRow());
        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(dataProvider).lookup("TestObjectField", "id", lookupFields);
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        List<MotechEvent> capturedEvents = captorEvent.getAllValues();

        assertEquals(asList("org.motechproject.message", createHandlerFailureSubject(task.getName(), DATA_SOURCE)),
                extract(capturedEvents, on(MotechEvent.class).getSubject()));

        assertFalse(task.isEnabled());
        assertEquals("task.error.objectOfTypeNotFound", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfDataProviderObjectNotContainsField() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setTaskActivities();
        task.setFailuresInRow(taskActivities.size());
        setAdditionalData(true);

        Map<String, String> lookupFields = new HashMap<>();
        lookupFields.put("id", "123456789");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        when(dataProvider.getName()).thenReturn("TEST");
        when(dataProvider.supports("TestObjectField")).thenReturn(true);
        when(dataProvider.lookup("TestObjectField", "id", lookupFields)).thenReturn(new Object());

        assertTrue(task.isEnabled());

        handler.handle(createEvent());

        assertEquals(5, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(dataProvider).lookup("TestObjectField", "id", lookupFields);
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        List<MotechEvent> capturedEvents = captorEvent.getAllValues();

        assertEquals(asList("org.motechproject.message", createHandlerFailureSubject(task.getName(), DATA_SOURCE)),
                extract(capturedEvents, on(MotechEvent.class).getSubject()));

        assertFalse(task.isEnabled());
        assertEquals("task.error.objectDoesNotContainField", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotSendEventIfDateFormatInManipulationIsNotValid() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setManipulation();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("manipulations", "{{trigger.startDate?dateTime(BadFormat)}}");

        handler.handle(createEvent());

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
        assertEquals("error.date.format", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldGetWarningWhenManipulationHaveMistake() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setManipulation();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        task.getActions().get(0).getValues().put("manipulations", "{{trigger.eventName?toUper}}");
        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));

        verify(eventRelay, times(2)).sendEventMessage(any(MotechEvent.class));
        verify(taskActivityService).addWarning(task, "task.warning.manipulation", "toUper");
    }

    @Test
    public void shouldPassFiltersCriteria() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setFilters();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(eventRelay, times(2)).sendEventMessage(any(MotechEvent.class));
        verify(taskActivityService).addSuccess(task);
    }

    @Test
    public void shouldNotPassFiltersCriteria() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setFilters();

        task.getTaskConfig().add(new FilterSet(asList(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, false, EXIST.getValue(), ""))));

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService, never()).save(task);
        verify(taskService, never()).getActionEventFor(task.getActions().get(0));
        verify(eventRelay, never()).sendEventMessage(any(MotechEvent.class));
    }

    @Test
    public void shouldSendEventForGivenTrigger() throws Exception {
        setTriggerEvent();
        setActionEvent();

        setManipulation();
        setDateField();
        setTimeField();
        setFilters();
        setAdditionalData(true);
        setLongField();
        setDoubleField();
        setBooleanField();

        setListField();
        setMapField();

        setNonRequiredField();

        Map<String, String> testObjectLookup = new HashMap<>();
        testObjectLookup.put("id", "123456789-6789");

        TestObject testObject = new TestObject();
        TestObjectField testObjectField = new TestObjectField();

        Map<String, String> testObjectFieldLookup = new HashMap<>();
        testObjectFieldLookup.put("id", "123456789");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        when(dataProvider.getName()).thenReturn("TEST");
        when(dataProvider.supports("TestObject")).thenReturn(true);
        when(dataProvider.lookup("TestObject", "id", testObjectLookup)).thenReturn(testObject);

        when(dataProvider.supports("TestObjectField")).thenReturn(true);
        when(dataProvider.lookup("TestObjectField", "id", testObjectFieldLookup)).thenReturn(testObjectField);

        ArgumentCaptor<MotechEvent> captor = ArgumentCaptor.forClass(MotechEvent.class);

        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(eventRelay, times(2)).sendEventMessage(captor.capture());
        verify(taskActivityService).addSuccess(task);

        List<MotechEvent> events = captor.getAllValues();

        assertEquals(asList(ACTION_SUBJECT, createHandlerSuccessSubject(task.getName())),
                extract(events, on(MotechEvent.class).getSubject()));

        MotechEvent motechEvent = (MotechEvent) CollectionUtils.find(events, new Predicate() {
            @Override
            public boolean evaluate(Object object) {
                return object instanceof MotechEvent && ((MotechEvent) object).getSubject().equalsIgnoreCase(ACTION_SUBJECT);
            }
        });

        assertEquals(ACTION_SUBJECT, motechEvent.getSubject());

        Map<String, Object> motechEventParameters = motechEvent.getParameters();

        assertNotNull(motechEventParameters);
        assertEquals(13, motechEventParameters.size());
        assertEquals(task.getActions().get(0).getValues().get("phone"), motechEventParameters.get("phone").toString());
        assertEquals("Hello 123456789, You have an appointment on 2012-11-20", motechEventParameters.get("message"));
        assertEquals("String manipulation: Event-Name, Date manipulation: 20121120", motechEventParameters.get("manipulations"));
        assertEquals(DateTime.parse(task.getActions().get(0).getValues().get("date"), DateTimeFormat.forPattern("yyyy-MM-dd HH:mm Z")), motechEventParameters.get("date"));
        assertEquals("test: 6789", motechEventParameters.get("dataSourceTrigger"));
        assertEquals("test: 6789", motechEventParameters.get("dataSourceObject"));
        assertEquals(DateTime.parse(task.getActions().get(0).getValues().get("time"), DateTimeFormat.forPattern("HH:mm Z")), motechEventParameters.get("time"));
        assertEquals(10000000000L, motechEventParameters.get("long"));
        assertEquals(true, motechEventParameters.get("boolean"));
        assertEquals(getExpectedList(), motechEventParameters.get("list"));
        assertEquals(getExpectedMap(), motechEventParameters.get("map"));
        assertNull(motechEventParameters.get("delivery_time"));
    }

    @Test
    public void shouldNotExecuteServiceMethodIfBundleContextIsNull() throws Exception {
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("throwException");
        actionEvent.setSubject(null);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);

        handler.handle(createEvent());

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), ACTION), captorEvent.getValue().getSubject());
        assertEquals("task.error.cantExecuteAction", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldNotExecuteServiceMethodIfServiceReferenceIsNull() throws Exception {
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("throwException");
        actionEvent.setSubject(null);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference(anyString())).thenReturn(null);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addWarning(task, "task.warning.serviceUnavailable", "TestService");
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), ACTION), captorEvent.getValue().getSubject());
        assertEquals("task.error.cantExecuteAction", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldThrowTaskExceptionWhenServiceMethodThrowException() throws Exception {
        TestService testService = new TestService();
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("throwException");
        actionEvent.setSubject(null);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference("TestService")).thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(testService);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), ACTION), captorEvent.getValue().getSubject());
        assertEquals("task.error.serviceMethodInvokeError", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldThrowTaskExceptionWhenServiceMethodNotFound() throws Exception {
        TestService testService = new TestService();
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");
        actionEvent.setSubject(null);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference("TestService")).thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(testService);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addError(eq(task), exceptionCaptor.capture(), eq(createEventParameters()));

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), ACTION), captorEvent.getValue().getSubject());
        assertEquals("task.error.notFoundMethodForService", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void shouldExecuteServiceMethod() throws Exception {
        TestService testService = new TestService();
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("execute");
        actionEvent.setSubject(null);

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference("TestService")).thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(testService);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addSuccess(task);

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay).sendEventMessage(captorEvent.capture());

        assertEquals(createHandlerSuccessSubject(task.getName()), captorEvent.getValue().getSubject());
    }

    @Test
    public void shouldSendEventIfAdditionalDataNotFound() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setAdditionalData(false);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference("TestService")).thenReturn(null);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addWarning(task, "task.warning.serviceUnavailable", actionEvent.getServiceInterface());
        verify(taskActivityService, times(2)).addWarning(task, "task.warning.notFoundObjectForType", "TestObjectField");
        verify(taskActivityService).addSuccess(task);

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());

        assertEquals(asList(ACTION_SUBJECT, createHandlerSuccessSubject(task.getName())),
                extract(captorEvent.getAllValues(), on(MotechEvent.class).getSubject()));
    }

    @Test
    public void shouldSendEventIfServiceIsNotAvailable() throws Exception {
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenReturn(actionEvent);
        when(bundleContext.getServiceReference("TestService")).thenReturn(null);

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskActivityService).addWarning(task, "task.warning.serviceUnavailable", actionEvent.getServiceInterface());
        verify(taskActivityService).addSuccess(task);

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());

        assertEquals(asList(ACTION_SUBJECT, createHandlerSuccessSubject(task.getName())),
                extract(captorEvent.getAllValues(), on(MotechEvent.class).getSubject()));
    }

    @Test
    public void shouldCaptureUnrecognizedError() throws Exception {
        setTriggerEvent();
        setActionEvent();

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenThrow(new RuntimeException());

        handler.setBundleContext(bundleContext);
        handler.handle(createEvent());
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        assertEquals(1, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(eventRelay).sendEventMessage(captorEvent.capture());
        verify(taskActivityService, never()).addSuccess(task);

        assertEquals(createHandlerFailureSubject(task.getName(), TRIGGER), captorEvent.getValue().getSubject());
    }

    @Test
    public void shouldExecuteTwoActions() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setSecondAction();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(any(TaskActionInformation.class))).thenReturn(actionEvent);

        ArgumentCaptor<MotechEvent> captor = ArgumentCaptor.forClass(MotechEvent.class);

        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(taskService).findActiveTasksForTriggerSubject(TRIGGER_SUBJECT);
        verify(taskService).getActionEventFor(task.getActions().get(0));
        verify(taskService).getActionEventFor(task.getActions().get(1));
        verify(eventRelay, times(3)).sendEventMessage(captor.capture());
        verify(taskActivityService).addSuccess(task);

        List<MotechEvent> events = captor.getAllValues();

        assertEquals(
                asList(ACTION_SUBJECT, ACTION_SUBJECT, createHandlerSuccessSubject(task.getName())),
                extract(events, on(MotechEvent.class).getSubject())
        );

        MotechEvent motechEventAction1 = events.get(0);

        assertEquals(ACTION_SUBJECT, motechEventAction1.getSubject());
        assertNotNull(motechEventAction1.getParameters());
        assertEquals(2, motechEventAction1.getParameters().size());
        assertEquals(task.getActions().get(0).getValues().get("phone"), motechEventAction1.getParameters().get("phone").toString());
        assertEquals("Hello 123456789, You have an appointment on 2012-11-20", motechEventAction1.getParameters().get("message"));

        MotechEvent motechEventAction2 = events.get(1);

        assertEquals(ACTION_SUBJECT, motechEventAction2.getSubject());
        assertNotNull(motechEventAction2.getParameters());
        assertEquals(2, motechEventAction2.getParameters().size());
        assertEquals(task.getActions().get(0).getValues().get("phone"), motechEventAction2.getParameters().get("phone").toString());
        assertEquals("Hello, world! I'm second action", motechEventAction2.getParameters().get("message"));
    }

    @Test
    public void shouldHandleFormatManipulation() throws Exception {
        setTriggerEvent();
        setActionEvent();
        setFormatManipulation();
        setAdditionalData(true);

        Map<String, String> testObjectLookup = new HashMap<>();
        testObjectLookup.put("id", "123456789-6789");

        TestObject testObject = new TestObject();
        TestObjectField testObjectField = new TestObjectField();

        Map<String, String> testObjectFieldLookup = new HashMap<>();
        testObjectFieldLookup.put("id", "123456789");

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(any(TaskActionInformation.class))).thenReturn(actionEvent);

        when(dataProvider.getName()).thenReturn("TEST");
        when(dataProvider.supports("TestObject")).thenReturn(true);
        when(dataProvider.lookup("TestObject", "id", testObjectLookup)).thenReturn(testObject);

        when(dataProvider.supports("TestObjectField")).thenReturn(true);
        when(dataProvider.lookup("TestObjectField", "id", testObjectFieldLookup)).thenReturn(testObjectField);

        ArgumentCaptor<MotechEvent> captor = ArgumentCaptor.forClass(MotechEvent.class);

        handler.handle(createEvent());

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(eventRelay, times(2)).sendEventMessage(captor.capture());

        MotechEvent event = captor.getAllValues().get(0);
        assertEquals("123456789 || 6789 || YourName", event.getParameters().get("format"));
    }

    @Test
    public void shouldHandleTriggerWithCustomParser() throws Exception {
        setTriggerEvent();
        setActionEvent();

        when(taskService.findActiveTasksForTriggerSubject(TRIGGER_SUBJECT)).thenReturn(tasks);
        when(taskService.getActionEventFor(any(TaskActionInformation.class))).thenReturn(actionEvent);
        when(taskService.findCustomParser(SampleTasksEventParser.PARSER_NAME)).thenReturn(new SampleTasksEventParser());

        ArgumentCaptor<MotechEvent> captor = ArgumentCaptor.forClass(MotechEvent.class);

        handler.handle(createEvent(true));

        assertEquals(0, task.getFailuresInRow());

        verify(taskService).save(task);
        verify(eventRelay, times(2)).sendEventMessage(captor.capture());

        MotechEvent event = captor.getAllValues().get(1);

        Map<String, Object> paramsMap = event.getParameters();

        assertTrue(paramsMap.containsKey("eve"));
        assertTrue(paramsMap.containsKey("ext"));
        assertTrue(paramsMap.containsKey("fac"));
        assertTrue(paramsMap.containsKey("lis"));

        assertEquals("eve", paramsMap.get("eve"));
        assertEquals("123", paramsMap.get("ext"));
        assertEquals("987", paramsMap.get("fac"));
        assertEquals("[1,", paramsMap.get("lis"));
    }

    @Test
    public void shouldScheduleTaskRetriesOnFailure() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(5);
        task.setRetryIntervalInMilliseconds(5000);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.findActiveTasksForTriggerSubject(triggerEvent.getSubject())).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenThrow(new RuntimeException());

        MotechEvent event = createEvent();
        handler.setBundleContext(bundleContext);
        handler.handle(event);
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        assertEquals(1, task.getFailuresInRow());
        verify(eventRelay, times(2)).sendEventMessage(captorEvent.capture());

        MotechEvent scheduleJobEvent = captorEvent.getAllValues().get(1);
        assertEquals(SCHEDULE_REPEATING_JOB, scheduleJobEvent.getSubject());

        Map<String, Object> parameters = scheduleJobEvent.getParameters();
        assertEquals(5, parameters.get(EventDataKeys.REPEAT_COUNT));
        // We send repeat interval time in seconds
        assertEquals(5, parameters.get(EventDataKeys.REPEAT_INTERVAL_TIME));
        assertEquals(task.getId(), parameters.get(EventDataKeys.TASK_ID));
        assertEquals(task.getTrigger().getEffectiveListenerRetrySubject(), parameters.get(EventDataKeys.JOB_SUBJECT));
    }

    @Test
    public void shouldNotScheduleTaskRetriesAgainOnFailure() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(5);
        task.setRetryIntervalInMilliseconds(5000);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.getTask(5L)).thenReturn(task);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenThrow(new RuntimeException());

        MotechEvent event = createEvent();
        event.getParameters().put(EventDataKeys.TASK_ID, 5L);

        handler.setBundleContext(bundleContext);
        handler.handleRetry(event);
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        assertEquals(1, task.getFailuresInRow());
        // since we already scheduled task retries, we should not send once again schedule job event
        verify(eventRelay).sendEventMessage(any(MotechEvent.class));
    }

    @Test
    public void shouldNotScheduleTaskRetryWhenNumberOfRetriesIsZero() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(0);
        task.setRetryIntervalInMilliseconds(0);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.findActiveTasksForTriggerSubject(triggerEvent.getSubject())).thenReturn(tasks);
        when(taskService.getActionEventFor(task.getActions().get(0))).thenThrow(new RuntimeException());

        MotechEvent event = createEvent();

        handler.setBundleContext(bundleContext);
        handler.handle(event);

        assertEquals(1, task.getFailuresInRow());
        // task number of retries is 0, we should not send schedule job event
        verify(eventRelay).sendEventMessage(any(MotechEvent.class));
    }

    @Test
    public void shouldUnscheduleTaskRetriesWhenSuccess() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(5);
        task.setRetryIntervalInMilliseconds(5000);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.getTask(5L)).thenReturn(task);
        when(taskService.getActionEventFor(any(TaskActionInformation.class))).thenReturn(actionEvent);

        MotechEvent event = createEvent();
        event.getParameters().put(EventDataKeys.TASK_ID, 5L);

        handler.setBundleContext(bundleContext);
        handler.handleRetry(event);
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        assertEquals(0, task.getFailuresInRow());
        verify(eventRelay, times(3)).sendEventMessage(captorEvent.capture());

        MotechEvent scheduleJobEvent = captorEvent.getAllValues().get(2);
        assertEquals(UNSCHEDULE_REPEATING_JOB, scheduleJobEvent.getSubject());

        Map<String, Object> parameters = scheduleJobEvent.getParameters();
        assertEquals(task.getTrigger().getEffectiveListenerRetrySubject(), parameters.get(EventDataKeys.JOB_SUBJECT));
    }

    @Test
    public void shouldUnscheduleTaskRetriesWhenTaskDeleted() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(5);
        task.setRetryIntervalInMilliseconds(5000);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.getTask(5L)).thenReturn(null);

        MotechEvent event = createEvent();
        event.getParameters().put(EventDataKeys.TASK_ID, 5L);
        event.getParameters().put(EventDataKeys.JOB_SUBJECT, task.getTrigger().getEffectiveListenerRetrySubject());

        handler.setBundleContext(bundleContext);
        handler.handleRetry(event);
        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        verify(eventRelay).sendEventMessage(captorEvent.capture());

        MotechEvent scheduleJobEvent = captorEvent.getValue();
        assertEquals(UNSCHEDULE_REPEATING_JOB, scheduleJobEvent.getSubject());

        Map<String, Object> parameters = scheduleJobEvent.getParameters();
        assertEquals(task.getTrigger().getEffectiveListenerRetrySubject(), parameters.get(EventDataKeys.JOB_SUBJECT));
    }

    @Test
    public void shouldUnscheduleTaskRetriesWhenTaskDisabled() throws Exception {
        setTriggerEvent();
        setActionEvent();

        task.setNumberOfRetries(5);
        task.setRetryIntervalInMilliseconds(5000);
        task.setEnabled(false);

        actionEvent.setServiceInterface("TestService");
        actionEvent.setServiceMethod("abc");

        when(taskService.getTask(5L)).thenReturn(task);

        MotechEvent event = createEvent();
        event.getParameters().put(EventDataKeys.TASK_ID, 5L);
        event.getParameters().put(EventDataKeys.JOB_SUBJECT, task.getTrigger().getEffectiveListenerRetrySubject());

        handler.setBundleContext(bundleContext);
        handler.handleRetry(event);

        ArgumentCaptor<MotechEvent> captorEvent = ArgumentCaptor.forClass(MotechEvent.class);

        verify(eventRelay).sendEventMessage(captorEvent.capture());

        MotechEvent scheduleJobEvent = captorEvent.getValue();
        assertEquals(UNSCHEDULE_REPEATING_JOB, scheduleJobEvent.getSubject());

        Map<String, Object> parameters = scheduleJobEvent.getParameters();
        assertEquals(task.getTrigger().getEffectiveListenerRetrySubject(), parameters.get(EventDataKeys.JOB_SUBJECT));
    }

    private void initTask() throws Exception {
        Map<String, String> actionValues = new HashMap<>();
        actionValues.put("phone", "123456");
        actionValues.put("message", "Hello {{trigger.externalId}}, You have an appointment on {{trigger.startDate}}");

        TaskTriggerInformation trigger = new TaskTriggerInformation("appointments", "Appointments", "appointments-bundle", "0.15", TRIGGER_SUBJECT, TRIGGER_SUBJECT);
        TaskActionInformation action = new TaskActionInformation("sms", "SMS", "sms-bundle", "0.15", ACTION_SUBJECT, actionValues);

        task = new Task();
        task.setName("name");
        task.setTrigger(trigger);
        task.addAction(action);
        task.setId(9l);
        task.setHasRegisteredChannel(true);
        tasks.add(task);
    }

    private void setSecondAction() {
        Map<String, String> actionValues = new HashMap<>();
        actionValues.put("phone", "123456");
        actionValues.put("message", "Hello, world! I'm second action");

        task.addAction(new TaskActionInformation("sms", "SMS", "sms-bundle", "0.15", ACTION_SUBJECT, actionValues));
    }

    private void setManipulation() {
        task.getActions().get(0).getValues().put("manipulations", "String manipulation: {{trigger.eventName?toUpper?toLower?capitalize?join(-)}}, Date manipulation: {{trigger.startDate?dateTime(yyyyMMdd)}}");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Manipulations").setKey("manipulations")
                .setType(TEXTAREA).build(), true);
    }

    private void setFormatManipulation() {
        task.getActions().get(0).getValues().put("format", "{{trigger.format?format({{trigger.externalId}},{{ad.12345.TestObject#2.field.id}},YourName)}}");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Format").setKey("format")
                .build(), true);
    }

    private void setDateField() {
        task.getActions().get(0).getValues().put("date", "2012-12-21 21:21 +0100");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Date").setKey("date")
                .setType(DATE).build(), true);
    }

    private void setTimeField() {
        task.getActions().get(0).getValues().put("time", "21:21 +0100");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Time").setKey("time")
                .setType(TIME).build(), true);
    }

    private void setLongField() {
        task.getActions().get(0).getValues().put("long", "10000000000");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Long").setKey("long")
                .setType(LONG).build(), true);
    }

    private void setBooleanField() {
        task.getActions().get(0).getValues().put("boolean", "true");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Boolean").setKey("boolean")
                .setType(BOOLEAN).build(), true);
    }

    private void setDoubleField() {
        task.getActions().get(0).getValues().put("double", "123.5");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Double").setKey("double")
                .setType(DOUBLE).build(), true);
    }

    private void setListField() {
        task.getActions().get(0).getValues().put("list", "4\n5\n{{trigger.list}}\n{{trigger.externalId}}\n{{ad.12345.TestObjectField#1.id}}");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("List").setKey("list")
                .setType(LIST).build(), true);
    }

    private void setMapField() {
        task.getActions().get(0).getValues().put("map", "key1:value\n{{trigger.map}}\n{{trigger.eventName}}:{{ad.12345.TestObjectField#1.id}}");
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Map").setKey("map")
                .setType(MAP).build(), true);
    }

    private void setAdditionalData(boolean isFail) {
        task.getActions().get(0).getValues().put("dataSourceTrigger", "test: {{ad.12345.TestObjectField#1.id}}");
        task.getActions().get(0).getValues().put("dataSourceObject", "test: {{ad.12345.TestObject#2.field.id}}");

        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Data source by trigger")
                .setKey("dataSourceTrigger").build(), true);
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Data source by data source object")
                .setKey("dataSourceObject").build(), true);

        task.getTaskConfig().add(new DataSource(TASK_DATA_PROVIDER_NAME, 4L, 1L, "TestObjectField", "id", asList(new Lookup("id", "{{trigger.externalId}}")), isFail));
        task.getTaskConfig().add(new DataSource(TASK_DATA_PROVIDER_NAME, 4L, 2L, "TestObject", "id", asList(new Lookup("id", "{{trigger.externalId}}-{{ad.12345.TestObjectField#1.id}}")), isFail));

        handler.addDataProvider(dataProvider);
    }

    private void setTriggerEvent() {
        List<EventParameter> triggerEventParameters = new ArrayList<>();
        triggerEventParameters.add(new EventParameter("ExternalID", "externalId"));
        triggerEventParameters.add(new EventParameter("StartDate", "startDate", DATE));
        triggerEventParameters.add(new EventParameter("EndDate", "endDate", DATE));
        triggerEventParameters.add(new EventParameter("FacilityId", "facilityId"));
        triggerEventParameters.add(new EventParameter("EventName", "eventName"));
        triggerEventParameters.add(new EventParameter("List", "list", LIST));
        triggerEventParameters.add(new EventParameter("Map", "map", MAP));

        triggerEvent = new TriggerEvent();
        triggerEvent.setSubject(TRIGGER_SUBJECT);
        triggerEvent.setEventParameters(triggerEventParameters);
    }

    private void setActionEvent() {
        SortedSet<ActionParameter> actionEventParameters = new TreeSet<>();

        actionEventParameters.add(new ActionParameterBuilder().setDisplayName("Phone").setKey("phone")
                .setType(INTEGER).setOrder(0).build());

        actionEventParameters.add(new ActionParameterBuilder().setDisplayName("Message").setKey("message")
                .setType(TEXTAREA).setOrder(1).build());

        actionEvent = new ActionEventBuilder().build();
        actionEvent.setSubject(ACTION_SUBJECT);
        actionEvent.setActionParameters(actionEventParameters);
    }

    private void setTaskActivities() {
        taskActivities = new ArrayList<>(5);
        taskActivities.add(new TaskActivity("Error1", task.getId(), ERROR));
        taskActivities.add(new TaskActivity("Error2", task.getId(), ERROR));
        taskActivities.add(new TaskActivity("Error3", task.getId(), ERROR));
        taskActivities.add(new TaskActivity("Error4", task.getId(), ERROR));
    }

    private void setFilters() {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, CONTAINS.getValue(), "ven"));
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, EXIST.getValue(), ""));
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, EQUALS.getValue(), "event name"));
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, EQUALS_IGNORE_CASE.getValue(), "EvEnT nAmE"));
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, STARTSWITH.getValue(), "ev"));
        filters.add(new Filter("EventName (Trigger)", "trigger.eventName", UNICODE, true, ENDSWITH.getValue(), "me"));
        filters.add(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, true, GT.getValue(), "19"));
        filters.add(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, true, LT.getValue(), "1234567891"));
        filters.add(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, true, EQ_NUMBER.getValue(), "123456789"));
        filters.add(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, true, EXIST.getValue(), ""));
        filters.add(new Filter("ExternalID (Trigger)", "trigger.externalId", INTEGER, false, GT.getValue(), "1234567891"));

        task.getTaskConfig().add(new FilterSet(filters));
    }

    private void setNonRequiredField() {
        actionEvent.addParameter(new ActionParameterBuilder().setDisplayName("Delivery time").setKey("delivery_time")
                .setType(DATE).setRequired(false).build(), true);
    }

    private MotechEvent createEvent() {
        return createEvent(false);
    }

    private MotechEvent createEvent(boolean withCustomParser) {
        Map<String, Object> param = createEventParameters();

        if (withCustomParser) {
            param.put(TasksEventParser.CUSTOM_PARSER_EVENT_KEY, SampleTasksEventParser.PARSER_NAME);
        }

        return new MotechEvent(TRIGGER_SUBJECT, param);
    }

    private Map<String, Object> createEventParameters() {
        Map<String, Object> param = new HashMap<>(4);
        param.put("externalId", 123456789);
        param.put("startDate", new LocalDate(2012, 11, 20));
        param.put("map", new HashMap<>(param));
        param.put("endDate", new LocalDate(2012, 11, 29));
        param.put("facilityId", 987654321);
        param.put("eventName", "event name");
        param.put("list", asList(1, 2, 3));
        param.put("format", "%s || %s || %s");

        return param;
    }

    private List<Object> getExpectedList() {
        List<Object> list = new ArrayList<>();
        list.addAll(asList("4", "5"));
        list.addAll(asList(1, 2, 3));
        list.add(123456789);
        list.add(6789);

        return list;
    }

    private Map<Object, Object> getExpectedMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put("externalId", 123456789);
        map.put("startDate", new LocalDate(2012, 11, 20));
        map.put("key1", "value");
        map.put("event name", 6789);

        return map;
    }

    private static <T> List<T> asList(T... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

}
