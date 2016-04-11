package org.motechproject.tasks.annotations;

import org.motechproject.tasks.domain.mds.ParameterType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks method parameter to be treated as action parameter.
 * <p/>
 * Each parameter in the given method has to have this annotation otherwise it will be
 * a problem with the proper execution of the channel action.
 *
 * @see TaskAction
 * @see TaskChannel
 * @since 0.19
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskActionParam {

    /**
     * Sets the display name of the action parameter.
     *
     * @return  the display name of the parameter
     */
    String displayName();

    /**
     * Sets the key of the action parameter.
     *
     * @return  the key of the parameter
     */
    String key();

    /**
     * Sets the type of the action parameter. The default value is "UNICODE".
     *
     * @return  the type of the parameter
     */
    ParameterType type() default ParameterType.UNICODE;

    /**
     * Defines whether the marked parameter is required or not. Default value is "true".
     *
     * @return  true if parameter is required, false otherwise
     */
    boolean required() default true;

}
