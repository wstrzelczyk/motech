package org.motechproject.mds.domain;

import org.motechproject.mds.util.ClassName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The <code>EntityInfo</code> class contains base information about the given entity like class
 * name or infrastructure classes name.
 *
 * @see org.motechproject.mds.service.JarGeneratorService
 */
public class EntityInfo {
    private String className;
    private String entityName;
    private String module;
    private String namespace;
    private String repository;
    private String interfaceName;
    private String serviceName;

    private boolean createEventFired;
    private boolean updateEventFired;
    private boolean deleteEventFired;

    private boolean restCreateEnabled;
    private boolean restReadEnabled;
    private boolean restUpdateEnabled;
    private boolean restDeleteEnabled;

    private List<FieldInfo> fieldsInfo;

    public String getName() {
        return ClassName.getSimpleName(className);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String[] getInfrastructure() {
        return new String[]{repository, interfaceName, serviceName};
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getRestId() {
        return ClassName.restId(entityName, module, namespace);
    }

    public boolean isCreateEventFired() {
        return createEventFired;
    }

    public void setCreateEventFired(boolean createEventFired) {
        this.createEventFired = createEventFired;
    }

    public boolean isUpdateEventFired() {
        return updateEventFired;
    }

    public void setUpdateEventFired(boolean updateEventFired) {
        this.updateEventFired = updateEventFired;
    }

    public boolean isDeleteEventFired() {
        return deleteEventFired;
    }

    public void setDeleteEventFired(boolean deleteEventFired) {
        this.deleteEventFired = deleteEventFired;
    }

    public boolean isRestCreateEnabled() {
        return restCreateEnabled;
    }

    public void setRestCreateEnabled(boolean restCreateEnabled) {
        this.restCreateEnabled = restCreateEnabled;
    }

    public boolean isRestReadEnabled() {
        return restReadEnabled;
    }

    public void setRestReadEnabled(boolean restReadEnabled) {
        this.restReadEnabled = restReadEnabled;
    }

    public boolean isRestUpdateEnabled() {
        return restUpdateEnabled;
    }

    public void setRestUpdateEnabled(boolean restUpdateEnabled) {
        this.restUpdateEnabled = restUpdateEnabled;
    }

    public boolean isRestDeleteEnabled() {
        return restDeleteEnabled;
    }

    public void setRestDeleteEnabled(boolean restDeleteEnabled) {
        this.restDeleteEnabled = restDeleteEnabled;
    }

    public List<FieldInfo> getFieldsInfo() {
        return fieldsInfo;
    }

    public void setFieldsInfo(List<FieldInfo> fieldsInfo) {
        this.fieldsInfo = fieldsInfo;
    }

    public static Collection<EntityInfo> entitiesWithAnyCRUDAction(Collection<EntityInfo> entityInfos) {
        Collection<EntityInfo> entitiesWithAnyCRUDAction = new ArrayList<>();
        for (EntityInfo entityInfo : entityInfos) {
            if (entityInfo.isCreateEventFired() || entityInfo.isUpdateEventFired() || entityInfo.isDeleteEventFired()) {
                entitiesWithAnyCRUDAction.add(entityInfo);
            }
        }
        return entitiesWithAnyCRUDAction;
    }

    public boolean supportAnyRestAccess() {
        return isRestCreateEnabled() || isRestReadEnabled() || isRestUpdateEnabled() ||
                isRestDeleteEnabled();
    }
}
