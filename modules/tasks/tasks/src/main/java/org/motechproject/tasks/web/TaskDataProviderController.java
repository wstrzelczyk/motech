package org.motechproject.tasks.web;

import org.motechproject.tasks.domain.mds.task.TaskDataProvider;
import org.motechproject.tasks.service.TaskDataProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Controller for managing Data Providers.
 */
@Controller
public class TaskDataProviderController {

    private TaskDataProviderService taskDataProviderService;

    /**
     * Controller constructor.
     *
     * @param taskDataProviderService  the task data provider service, not null
     */
    @Autowired
    public TaskDataProviderController(TaskDataProviderService taskDataProviderService) {
        this.taskDataProviderService = taskDataProviderService;
    }

    /**
     * Returns the list of all Data Providers.
     *
     * @return  the list of all Data Providers
     */
    @RequestMapping(value = "datasource", method = RequestMethod.GET)
    @ResponseBody
    public List<TaskDataProvider> getAllDataProviders() {
        return taskDataProviderService.getProviders();
    }
}
