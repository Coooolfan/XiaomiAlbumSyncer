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

package org.noear.solon.gradle.tasks.aot;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Custom {@link JavaExec} task for ahead-of-time processing of a Solom application.
 *
 * @author Andy Wilkinson
 */
@CacheableTask
public abstract class ProcessAot extends JavaExec {


    private final DirectoryProperty sourcesDir;

    private final DirectoryProperty resourcesDir;

    private final DirectoryProperty classesDir;

    private final Property<String> groupId;

    private final Property<String> artifactId;

    @Input
    @Optional
    public abstract ListProperty<String> getEnvs();

    @Input
    @Optional
    public abstract Property<String> getNativeBuildArgs();

    public ProcessAot() {
        this.sourcesDir = getProject().getObjects().directoryProperty();
        this.resourcesDir = getProject().getObjects().directoryProperty();
        this.classesDir = getProject().getObjects().directoryProperty();
        this.groupId = getProject().getObjects().property(String.class);
        this.artifactId = getProject().getObjects().property(String.class);
        getMainClass().set("org.noear.solon.aot.SolonAotProcessor");
    }

    /**
     * The group ID of the application that is to be processed ahead-of-time.
     *
     * @return the group ID property
     */
    @Input
    public final Property<String> getGroupId() {
        return this.groupId;
    }

    /**
     * The artifact ID of the application that is to be processed ahead-of-time.
     *
     * @return the artifact ID property
     */
    @Input
    public final Property<String> getArtifactId() {
        return this.artifactId;
    }

    /**
     * The directory to which AOT-generated sources should be written.
     *
     * @return the sources directory property
     */
    @OutputDirectory
    public final DirectoryProperty getSourcesOutput() {
        return this.sourcesDir;
    }

    /**
     * The directory to which AOT-generated resources should be written.
     *
     * @return the resources directory property
     */
    @OutputDirectory
    public final DirectoryProperty getResourcesOutput() {
        return this.resourcesDir;
    }

    /**
     * The directory to which AOT-generated classes should be written.
     *
     * @return the classes directory property
     */
    @OutputDirectory
    public final DirectoryProperty getClassesOutput() {
        return this.classesDir;
    }


    /**
     * Returns the main class of the application that is to be processed ahead-of-time.
     *
     * @return the application main class property
     */
    @Input
    public abstract Property<String> getApplicationMainClass();

    @Override
    @TaskAction
    public void exec() {
        List<String> aotArguments = new ArrayList<>();
        aotArguments.add(getApplicationMainClass().get());
        aotArguments.add(getClassesOutput().getAsFile().get().getAbsolutePath());
        aotArguments.add(getSourcesOutput().getAsFile().get().getAbsolutePath());
        aotArguments.add(getGroupId().get());
        aotArguments.add(getArtifactId().get());

        String nativeBuildArgs = getNativeBuildArgs().getOrNull();
        aotArguments.add(nativeBuildArgs == null ? "" : nativeBuildArgs);

        List<String> envs = getEnvs().getOrNull();
        if (envs != null && !envs.isEmpty()) {
            aotArguments.add("--solon.env=" + String.join(",", envs));
        }

        List<String> arguments = super.getArgs();
        if (arguments != null) {
            arguments.stream().filter(Objects::nonNull).forEach(aotArguments::add);
        }

        setArgs(aotArguments);
        super.exec();
    }

}
