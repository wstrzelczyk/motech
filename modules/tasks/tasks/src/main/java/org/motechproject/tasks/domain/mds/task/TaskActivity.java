package org.motechproject.tasks.domain.mds.task;

import org.joda.time.DateTime;
import org.motechproject.commons.date.util.DateTimeSourceUtil;
import org.motechproject.mds.annotations.Access;
import org.motechproject.mds.annotations.CrudEvents;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.mds.annotations.Ignore;
import org.motechproject.mds.event.CrudEventType;
import org.motechproject.mds.util.SecurityMode;
import org.motechproject.tasks.constants.TasksRoles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single task activity. Task activity is a historical entry about a task execution.
 */
@Entity(nonEditable = true)
@CrudEvents(CrudEventType.NONE)
@Access(value = SecurityMode.PERMISSIONS, members = {TasksRoles.MANAGE_TASKS})
public class TaskActivity implements Comparable<TaskActivity> {

    @Field(displayName = "Message")
    private String message;

    @Field(displayName = "Task", required = true)
    private Long task;

    @Field(displayName = "Fields")
    private List<String> fields;

    @Field(displayName = "Date", required = true)
    private DateTime date;

    @Field(displayName = "Activity Type")
    private TaskActivityType activityType;

    @Field(displayName = "StackTrace element", type = "text")
    private String stackTraceElement;

    @Field(displayName = "Parameters")
    private Map<String, Object> parameters;

    /**
     * Constructor.
     */
    public TaskActivity() {
        this(null, null, null);
    }

    /**
     * Constructor.
     *
     * @param message  the activity message
     * @param task  the activity task ID
     * @param activityType  the activity type
     */
    public TaskActivity(String message, Long task, TaskActivityType activityType) {
        this(message, new ArrayList<String>(), task, activityType);
    }

    /**
     * Constructor.
     *
     * @param message  the activity message
     * @param field  the field name
     * @param task  the activity task ID
     * @param activityType  the activity type
     */
    public TaskActivity(String message, String field, Long task, TaskActivityType activityType) {
        this(message, new ArrayList<>(Arrays.asList(field)), task, activityType);
    }

    /**
     * Constructor.
     *
     * @param message  the activity message
     * @param fields  the field names
     * @param task  the activity task ID
     * @param activityType  the activity type
     */
    public TaskActivity(String message, List<String> fields, Long task, TaskActivityType activityType) {
        this(message, fields, task, activityType, null, null);
    }

    /**
     * Constructor.
     *
     * @param message  the activity message
     * @param fields  the field names
     * @param task  the activity ID
     * @param activityType  the activity type
     * @param stackTraceElement  the stack trace that caused the task failure
     */
    public TaskActivity(String message, List<String> fields, Long task, TaskActivityType activityType, String stackTraceElement) {
        this(message, fields, task, activityType, stackTraceElement, null);
    }

    /**
     * Constructor.
     *
     * @param message  the activity message
     * @param fields  the field names
     * @param task  the activity ID
     * @param activityType  the activity type
     * @param stackTraceElement  the stack trace that caused the task failure
     * @param parameters the parameters used by the task in this execution
     */
    public TaskActivity(String message, List<String> fields, Long task, TaskActivityType activityType, String stackTraceElement,
                        Map<String, Object> parameters) {
        this.message = message;
        this.fields = fields;
        this.task = task;
        this.date = DateTimeSourceUtil.now();
        this.activityType = activityType;
        this.stackTraceElement = stackTraceElement;
        this.parameters = parameters;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public Long getTask() {
        return task;
    }

    public void setTask(final Long task) {
        this.task = task;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    @Ignore
    public void setField(String field) {
        if (fields == null) {
            fields = new ArrayList<>();
        } else {
            fields.clear();
        }
        fields.add(field);
    }

    public DateTime getDate() {
        return date;
    }

    public void setDate(final DateTime date) {
        this.date = date;
    }

    public TaskActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(final TaskActivityType activityType) {
        this.activityType = activityType;
    }

    public String getStackTraceElement() {
        return stackTraceElement;
    }

    public void setStackTraceElement(String stackTraceElement) {
        this.stackTraceElement = stackTraceElement;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final TaskActivity other = (TaskActivity) obj;

        return Objects.equals(this.message, other.message) &&
                Objects.equals(this.task, other.task) &&
                Objects.equals(this.fields, other.fields) &&
                Objects.equals(getDate(), other.getDate()) &&
                Objects.equals(this.activityType, other.activityType) &&
                Objects.equals(this.stackTraceElement, other.stackTraceElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, task, fields, getDate(), activityType, stackTraceElement);
    }

    @Override
    public String toString() {
        return String.format("TaskActivity{message='%s', task=%d, field='%s', date=%s, activityType=%s, stackTrace=%s}",
                message, task, fields, date, activityType, stackTraceElement);
    }

    @Override
    public int compareTo(TaskActivity o) {
        return getDate().compareTo(o.getDate());
    }
}
