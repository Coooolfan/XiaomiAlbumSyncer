/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.noear.solon.gradle.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.noear.solon.gradle.tasks.aot.ProcessAot;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Gradle plugin for Solom AOT.
 *
 * @author Andy Wilkinson
 */
public class SolonAotPlugin implements Plugin<Project> {

    /**
     * Name of the main {@code aot} {@link SourceSet source set}.
     */
    public static final String AOT_SOURCE_SET_NAME = "aot";


    /**
     * Name of the default {@link ProcessAot} task.
     */
    public static final String PROCESS_AOT_TASK_NAME = "processAot";

    @Override
    public void apply(Project project) {
        PluginContainer plugins = project.getPlugins();
        plugins.withType(JavaPlugin.class).all((javaPlugin) -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            SourceSet aotSourceSet = configureSourceSet(project, AOT_SOURCE_SET_NAME, mainSourceSet);
            plugins.withType(SolonPlugin.class).all((bootPlugin) -> {
                registerProcessAotTask(project, aotSourceSet, mainSourceSet);
            });
        });
    }

    private SourceSet configureSourceSet(Project project, String newSourceSetName, SourceSet existingSourceSet) {
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
        return sourceSets.create(newSourceSetName, (sourceSet) -> {
            existingSourceSet.setRuntimeClasspath(existingSourceSet.getRuntimeClasspath().plus(sourceSet.getOutput()));
            project.getConfigurations()
                    .getByName(sourceSet.getCompileClasspathConfigurationName())
                    .attributes((attributes) -> {
                        configureClassesAndResourcesLibraryElementsAttribute(project, attributes);
                        configureJavaRuntimeUsageAttribute(project, attributes);
                    });
        });
    }

    private void configureClassesAndResourcesLibraryElementsAttribute(Project project, AttributeContainer attributes) {
        LibraryElements classesAndResources = project.getObjects()
                .named(LibraryElements.class, LibraryElements.CLASSES_AND_RESOURCES);
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, classesAndResources);
    }

    private void configureJavaRuntimeUsageAttribute(Project project, AttributeContainer attributes) {
        Usage javaRuntime = project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME);
        attributes.attribute(Usage.USAGE_ATTRIBUTE, javaRuntime);
    }

    private void registerProcessAotTask(Project project, SourceSet aotSourceSet, SourceSet mainSourceSet) {
        TaskProvider<ResolveMainClassName> resolveMainClassName = project.getTasks()
                .named(SolonPlugin.RESOLVE_MAIN_CLASS_NAME_TASK_NAME, ResolveMainClassName.class);
        Configuration aotClasspath = createAotProcessingClasspath(project, PROCESS_AOT_TASK_NAME, mainSourceSet,
                Set.of(SolonPlugin.DEVELOPMENT_ONLY_CONFIGURATION_NAME));
        project.getDependencies().add(aotClasspath.getName(), project.files(mainSourceSet.getOutput()));
        Configuration compileClasspath = project.getConfigurations()
                .getByName(aotSourceSet.getCompileClasspathConfigurationName());
        compileClasspath.extendsFrom(aotClasspath);
        Provider<Directory> resourcesOutput = project.getLayout()
                .getBuildDirectory()
                .dir("generated/" + aotSourceSet.getName() + "Resources");
        TaskProvider<ProcessAot> processAot = project.getTasks()
                .register(PROCESS_AOT_TASK_NAME, ProcessAot.class, (task) -> {
                    configureAotTask(project, aotSourceSet, task, resourcesOutput);
                    task.getApplicationMainClass()
                            .set(resolveMainClassName.flatMap(ResolveMainClassName::readMainClassName));
                    task.setClasspath(aotClasspath);
                });
        aotSourceSet.getJava().srcDir(processAot.map(ProcessAot::getSourcesOutput));
        aotSourceSet.getResources().srcDir(resourcesOutput);
        ConfigurableFileCollection classesOutputFiles = project.files(processAot.map(ProcessAot::getClassesOutput));
        mainSourceSet.setRuntimeClasspath(mainSourceSet.getRuntimeClasspath().plus(classesOutputFiles));
        project.getDependencies().add(aotSourceSet.getImplementationConfigurationName(), classesOutputFiles);
        configureDependsOn(project, aotSourceSet, processAot);
    }

    private void configureAotTask(Project project, SourceSet sourceSet, ProcessAot task,
                                  Provider<Directory> resourcesOutput) {
        task.getSourcesOutput()
                .set(project.getLayout().getBuildDirectory().dir("generated/" + sourceSet.getName() + "Sources"));
        task.getResourcesOutput().set(resourcesOutput);
        task.getClassesOutput()
                .set(project.getLayout().getBuildDirectory().dir("generated/" + sourceSet.getName() + "Classes"));
        task.getGroupId().set(project.provider(() -> String.valueOf(project.getGroup())));
        task.getArtifactId().set(project.provider(project::getName));
        configureToolchainConvention(project, task);
    }

    private void configureToolchainConvention(Project project, ProcessAot aotTask) {
        JavaToolchainSpec toolchain = project.getExtensions().getByType(JavaPluginExtension.class).getToolchain();
        JavaToolchainService toolchainService = project.getExtensions().getByType(JavaToolchainService.class);
        aotTask.getJavaLauncher().convention(toolchainService.launcherFor(toolchain));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Configuration createAotProcessingClasspath(Project project, String taskName, SourceSet inputSourceSet,
                                                       Set<String> developmentOnlyConfigurationNames) {
        Configuration base = project.getConfigurations()
                .getByName(inputSourceSet.getRuntimeClasspathConfigurationName());
        return project.getConfigurations().create(taskName + "Classpath", (classpath) -> {
            classpath.setCanBeConsumed(false);
            if (!classpath.isCanBeResolved()) {
                throw new IllegalStateException("Unexpected");
            }
            classpath.setCanBeResolved(true);
            classpath.setDescription("Classpath of the " + taskName + " task.");
            removeDevelopmentOnly(base.getExtendsFrom(), developmentOnlyConfigurationNames)
                    .forEach(classpath::extendsFrom);
            classpath.attributes((attributes) -> {
                ProviderFactory providers = project.getProviders();
                AttributeContainer baseAttributes = base.getAttributes();
                for (Attribute attribute : baseAttributes.keySet()) {
                    attributes.attributeProvider(attribute,
                            providers.provider(() -> baseAttributes.getAttribute(attribute)));
                }
            });
        });
    }

    private Stream<Configuration> removeDevelopmentOnly(Set<Configuration> configurations,
                                                        Set<String> developmentOnlyConfigurationNames) {
        return configurations.stream()
                .filter((configuration) -> !developmentOnlyConfigurationNames.contains(configuration.getName()));
    }

    private void configureDependsOn(Project project, SourceSet aotSourceSet,
                                    TaskProvider<? extends ProcessAot> processAot) {
        project.getTasks()
                .named(aotSourceSet.getProcessResourcesTaskName())
                .configure((processResources) -> processResources.dependsOn(processAot));
    }


    void repairKotlinPluginDamage(Project project) {
        project.getPlugins().withType(JavaPlugin.class).configureEach((javaPlugin) -> {
            repairKotlinPluginDamage(project, SolonAotPlugin.AOT_SOURCE_SET_NAME);
        });
    }

    private void repairKotlinPluginDamage(Project project, String sourceSetName) {
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
        Configuration compileClasspath = project.getConfigurations()
                .getByName(sourceSets.getByName(sourceSetName).getCompileClasspathConfigurationName());
        configureJavaRuntimeUsageAttribute(project, compileClasspath.getAttributes());
    }

}
