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

package org.noear.solon.gradle.tasks.bundling;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.util.Set;

/**
 * Maps from {@link File} to {@link ComponentArtifactIdentifier}.
 *
 * @author Madhura Bhave
 * @author Scott Frederick
 * @author Phillip Webb
 * @author Paddy Drury
 * @author Andy Wilkinson
 */
class ResolvedDependencies {


    private final ListProperty<ComponentArtifactIdentifier> artifactIds;

    private final ListProperty<File> artifactFiles;

    ResolvedDependencies(Project project) {
        this.artifactIds = project.getObjects().listProperty(ComponentArtifactIdentifier.class);
        this.artifactFiles = project.getObjects().listProperty(File.class);
    }

    @Input
    ListProperty<ComponentArtifactIdentifier> getArtifactIds() {
        return this.artifactIds;
    }

    @Classpath
    ListProperty<File> getArtifactFiles() {
        return this.artifactFiles;
    }

    void resolvedArtifacts(Provider<Set<ResolvedArtifactResult>> resolvedArtifacts) {
        this.artifactFiles.addAll(
                resolvedArtifacts.map((artifacts) -> artifacts.stream().map(ResolvedArtifactResult::getFile).toList()));
        this.artifactIds.addAll(
                resolvedArtifacts.map((artifacts) -> artifacts.stream().map(ResolvedArtifactResult::getId).toList()));
    }

}
