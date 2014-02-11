package org.motechproject.mds.service.impl.internal;

import javassist.CtClass;
import org.apache.commons.collections.CollectionUtils;
import org.eclipse.gemini.blueprint.util.OsgiBundleUtils;
import org.motechproject.mds.builder.ClassData;
import org.motechproject.mds.builder.EnhancedClassData;
import org.motechproject.mds.builder.EntityBuilder;
import org.motechproject.mds.builder.EntityInfrastructureBuilder;
import org.motechproject.mds.builder.EntityMetadataBuilder;
import org.motechproject.mds.builder.MDSClassLoader;
import org.motechproject.mds.domain.Entity;
import org.motechproject.mds.enhancer.MdsJDOEnhancer;
import org.motechproject.mds.ex.EntityCreationException;
import org.motechproject.mds.javassist.JavassistHelper;
import org.motechproject.mds.javassist.MotechClassPool;
import org.motechproject.mds.repository.AllEntities;
import org.motechproject.mds.service.BaseMdsService;
import org.motechproject.mds.service.MDSConstructor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.jdo.metadata.JDOMetadata;
import java.util.List;

/**
 * Default implmenetation of {@link org.motechproject.mds.service.MDSConstructor} interface.
 */
@Service
public class MDSConstructorImpl extends BaseMdsService implements MDSConstructor {
    private static final Logger LOG = LoggerFactory.getLogger(MDSConstructorImpl.class);

    private MdsJDOEnhancer enhancer;

    private EntityBuilder entityBuilder;
    private EntityInfrastructureBuilder infrastructureBuilder;
    private EntityMetadataBuilder metadataBuilder;
    private BundleContext bundleContext;
    private AllEntities allEntities;

    @Override
    @Transactional
    public void constructEntity(Entity entity) {
        CtClass existingClass = MotechClassPool.getDefault().getOrNull(entity.getClassName());

        if (existingClass == null) {
            // just add a class
            constructEntity(entity, new MDSClassLoader());
        } else {
            // editing a class requires reloading the classLoader and regenerating the entities
            MDSClassLoader.reloadClassLoader();
            generateAllEntities();
        }
    }

    private void constructEntity(Entity entity, MDSClassLoader tmpClassLoader) {
        try {
            ClassData classData;

            // create the initial class, for DDEs we extend the class from the declaring bundle
            if (entity.isDDE()) {
                Bundle declaringBundle = OsgiBundleUtils.findBundleBySymbolicName(bundleContext, "org.motechproject.motech-event-aggregation");

                if (declaringBundle == null) {
                    throw new EntityCreationException("Declaring bundle unavailable for entity" + entity.getClassName());
                } else {
                    classData = entityBuilder.buildDDE(entity, declaringBundle);
                    // make a copy of parent for enhancement purposes
                    CtClass copyOfOriginal = JavassistHelper.loadClass(declaringBundle, entity.getClassName());

                    tmpClassLoader.defineClass(entity.getClassName(), copyOfOriginal.toBytecode());
                    MDSClassLoader.getInstance().defineClass(entity.getClassName(), copyOfOriginal.toBytecode());
                }
            } else {
                classData = entityBuilder.build(entity);
            }
            // we need a temporary classloader to define initial classes before enhancement

            tmpClassLoader.defineClass(classData);

            EnhancedClassData enhancedClassData = enhancer.enhance(entity, classData.getBytecode(), tmpClassLoader);
            MotechClassPool.registerEnhancedData(enhancedClassData);

            Class<?> clazz = MDSClassLoader.getInstance().defineClass(enhancedClassData);

            JDOMetadata jdoMetadata = metadataBuilder.createBaseEntity(
                    getPersistenceManagerFactory().newMetadata(), entity);

            getPersistenceManagerFactory().registerMetadata(jdoMetadata);

            buildInfrastructure(clazz);
        } catch (Exception e) {
            throw new EntityCreationException(e);
        }
    }

    @Override
    public void generateAllEntities() {
        MDSClassLoader tmpClassLoader = new MDSClassLoader();

        List<Entity> entities = allEntities.retrieveAll();

        for (Entity entity : entities) {
            if (!entity.isDraft()) {
                try {
                    constructEntity(entity, tmpClassLoader);
                } catch (Exception e) {
                    LOG.error("Unable to process entity " + entity.getClassName(), e);
                }
            }
        }
    }

    private void buildInfrastructure(Class<?> clazz) {
        List<ClassData> classes = infrastructureBuilder.buildInfrastructure(clazz);

        if (CollectionUtils.isNotEmpty(classes)) {
            for (ClassData classData : classes) {
                MDSClassLoader.getInstance().defineClass(classData.getClassName(), classData.getBytecode());
            }
        }
    }


    @Autowired
    public void setEnhancer(MdsJDOEnhancer enhancer) {
        this.enhancer = enhancer;
    }

    @Autowired
    public void setEntityBuilder(EntityBuilder entityBuilder) {
        this.entityBuilder = entityBuilder;
    }

    @Autowired
    public void setInfrastructureBuilder(EntityInfrastructureBuilder infrastructureBuilder) {
        this.infrastructureBuilder = infrastructureBuilder;
    }

    @Autowired
    public void setMetadataBuilder(EntityMetadataBuilder metadataBuilder) {
        this.metadataBuilder = metadataBuilder;
    }

    @Autowired
    public void setAllEntities(AllEntities allEntities) {
        this.allEntities = allEntities;
    }

    @Autowired
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
