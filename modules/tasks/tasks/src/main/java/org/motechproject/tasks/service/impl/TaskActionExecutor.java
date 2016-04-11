package org.motechproject.tasks.service.impl;

import com.google.common.collect.Multimap;
import org.motechproject.commons.api.MotechException;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.tasks.domain.mds.channel.ActionEvent;
import org.motechproject.tasks.domain.mds.channel.ActionParameter;
import org.motechproject.tasks.domain.KeyInformation;
import org.motechproject.tasks.domain.mds.ParameterType;
import org.motechproject.tasks.domain.mds.task.Task;
import org.motechproject.tasks.domain.mds.task.TaskActionInformation;
import org.motechproject.tasks.exception.ActionNotFoundException;
import org.motechproject.tasks.exception.TaskHandlerException;
import org.motechproject.tasks.service.util.KeyEvaluator;
import org.motechproject.tasks.service.TaskActivityService;
import org.motechproject.tasks.service.util.TaskContext;
import org.motechproject.tasks.service.TaskService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static org.motechproject.tasks.domain.mds.ParameterType.LIST;
import static org.motechproject.tasks.domain.mds.ParameterType.MAP;
import static org.motechproject.tasks.constants.TaskFailureCause.ACTION;
import static org.motechproject.tasks.constants.TaskFailureCause.TRIGGER;

/**
 * Builds action parameters from  {@link TaskContext} and executes the action by invoking its service or raising its event.
 */
@Component
public class TaskActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskActionExecutor.class);

    private BundleContext bundleContext;
    private EventRelay eventRelay;

    private TaskService taskService;
    private TaskActivityService activityService;
    private KeyEvaluator keyEvaluator;

    @Autowired
    public TaskActionExecutor(TaskService taskService, TaskActivityService activityService,
                       EventRelay eventRelay) {
        this.eventRelay = eventRelay;
        this.taskService = taskService;
        this.activityService = activityService;
    }

    /**
     * Executes the action for the given task.
     *
     * @param task  the task for which its action should be executed, not null
     * @param actionInformation  the information about the action, not null
     * @param taskContext  the context of the current task execution, not null
     * @throws TaskHandlerException when the task couldn't be executed
     */
    public void execute(Task task, TaskActionInformation actionInformation, TaskContext taskContext) throws TaskHandlerException {
        LOGGER.info("Executing task action: {} from task: {}", actionInformation.getName(), task.getName());
        this.keyEvaluator = new KeyEvaluator(taskContext);
        ActionEvent action = getActionEvent(actionInformation);
        Map<String, Object> parameters = createParameters(actionInformation, action);
        LOGGER.debug("Parameters created: {} for task action: {}", parameters.toString(), action.getName());

        if (action.hasService() && bundleContext != null) {
            if (callActionServiceMethod(action, parameters)) {
                LOGGER.info("Action: {} from task: {} was executed through an OSGi service call", actionInformation.getName(), task.getName());
                return;
            }
            LOGGER.info("There is no service: {}", action.getServiceInterface());
            activityService.addWarning(task, "task.warning.serviceUnavailable", action.getServiceInterface());
        }

        if (!action.hasSubject()) {
            throw new TaskHandlerException(ACTION, "task.error.cantExecuteAction");
        } else {
            LOGGER.info("Event: {} was sent", action.getSubject());
            eventRelay.sendEventMessage(new MotechEvent(action.getSubject(), parameters));
        }
    }

    private ActionEvent getActionEvent(TaskActionInformation actionInformation)
            throws TaskHandlerException {
        ActionEvent action;

        try {
            action = taskService.getActionEventFor(actionInformation);
        } catch (ActionNotFoundException e) {
            throw new TaskHandlerException(TRIGGER, "task.error.actionNotFound", e);
        }

        return action;
    }

    private Map<String, Object> createParameters(TaskActionInformation info,
                                         ActionEvent action) throws TaskHandlerException {
        SortedSet<ActionParameter> actionParameters = action.getActionParameters();
        Map<String, Object> parameters = new HashMap<>(actionParameters.size());

        for (ActionParameter actionParameter : actionParameters) {
            String key = actionParameter.getKey();

            if (info.getValues().containsKey(key)) {
                String template = info.getValues().get(key);

                if (template == null) {
                    throw new TaskHandlerException(
                        TRIGGER, "task.error.templateNull", key, action.getDisplayName()
                    );
                }

                switch (actionParameter.getType()) {
                    case LIST:
                        parameters.put(key, convertToList((List<String>) LIST.parse(template)));
                        break;
                    case MAP:
                        parameters.put(key, convertToMap(template));
                        break;
                    default:
                        try {
                            String userInput = keyEvaluator.evaluateTemplateString(template);
                            Object obj = actionParameter.getType().parse(userInput);
                            parameters.put(key, obj);
                        } catch (MotechException ex) {
                            throw new TaskHandlerException(TRIGGER, ex.getMessage(), ex, key);
                        }
                }
            } else {
                if (actionParameter.isRequired()) {
                    throw new TaskHandlerException(
                        TRIGGER, "task.error.taskActionNotContainsField",
                        action.getDisplayName(), key
                    );
                } else if (actionParameter.getType() == MAP) {
                    parameters.put(key, new HashMap<>());
                } else if (actionParameter.getType() == LIST) {
                    parameters.put(key, new ArrayList<>());
                } else {
                    parameters.put(key, null);
                }
            }
        }

        return parameters;
    }

    private Map<Object, Object> convertToMap(String template) throws TaskHandlerException {
        String[] rows = template.split("(\\r)?\\n");
        Map<Object, Object> tempMap = new HashMap<>(rows.length);

        for (String row : rows) {
            String[] array = row.split(":", 2);
            Object mapKey;
            Object mapValue;

            switch (array.length) {
                case 2:
                    array[1] = array[1].trim();
                    mapKey = getValue(array[0]);
                    mapValue = getValue(array[1]);

                    tempMap.put(
                        ParameterType.getType(mapKey.getClass()).parse(keyEvaluator.evaluateTemplateString(array[0])),
                        ParameterType.getType(mapValue.getClass()).parse(keyEvaluator.evaluateTemplateString(array[1]))
                    );
                    break;
                case 1:
                    mapValue = getValue(array[0]);
                    if (mapValue instanceof Multimap) {
                        tempMap.putAll(((Multimap) mapValue).asMap());
                    } else {
                        tempMap.putAll((Map) mapValue);
                    }
                    break;
                default:
            }
        }
        return tempMap;
    }

    private List<Object> convertToList(List<String> templates) throws TaskHandlerException {
        List<Object> tempList = new ArrayList<>();

        for (String template : templates) {
            Object value = getValue(template.trim());

            if (value instanceof Collection) {
                tempList.addAll((Collection) value);
            } else {
                tempList.add(ParameterType.getType(value.getClass()).parse(keyEvaluator.evaluateTemplateString(template)));
            }
        }

        return tempList;
    }

    private Object getValue(String row) throws TaskHandlerException {
        List<KeyInformation> keys = KeyInformation.parseAll(row);

        Object result;
        if (keys.isEmpty()) {
            result = row;
        } else {
            KeyInformation rowKeyInfo = keys.get(0);
            result = keyEvaluator.getValue(rowKeyInfo);
        }

        return result;
    }

    private boolean callActionServiceMethod(ActionEvent action, Map<String, Object> parameters)
            throws TaskHandlerException {
        ServiceReference reference = bundleContext.getServiceReference(
                action.getServiceInterface()
        );
        boolean serviceAvailable = reference != null;

        if (serviceAvailable) {
            Object service = bundleContext.getService(reference);
            String serviceMethod = action.getServiceMethod();
            MethodHandler methodHandler = new MethodHandler(action, parameters);

            try {
                Method method = service.getClass().getMethod(serviceMethod, methodHandler.getClasses());

                try {
                    method.invoke(service, methodHandler.getObjects());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new TaskHandlerException(
                            ACTION, "task.error.serviceMethodInvokeError", e,
                            serviceMethod, action.getServiceInterface()
                    );
                }
            } catch (NoSuchMethodException e) {
                throw new TaskHandlerException(
                        ACTION, "task.error.notFoundMethodForService", e,
                        serviceMethod, action.getServiceInterface()
                );
            }
        }

        return serviceAvailable;
    }

    void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
