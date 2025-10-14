/*
 * Copyright 2012-2023 the original author or authors.
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

import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Support class for implementations of {@link SolonArchive}.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @see SolonJar
 * @see
 */
class SolonArchiveSupport {

    private static final byte[] ZIP_FILE_HEADER = new byte[]{'P', 'K', 3, 4};
    private static final String UNSPECIFIED_VERSION = "unspecified";

    SolonArchiveSupport() {

    }

    void configureManifest(Manifest manifest, String mainClass, String jdkVersion, String implementationTitle, Object implementationVersion) {
        Attributes attributes = manifest.getAttributes();
        attributes.putIfAbsent("Main-Class", mainClass);
        attributes.computeIfAbsent("Solon-Version", (name) -> determineSolonVersion());

        attributes.putIfAbsent("Build-Jdk", jdkVersion);
        attributes.putIfAbsent("Implementation-Title", implementationTitle);
        if (implementationVersion != null) {
            String versionString = implementationVersion.toString();
            if (!UNSPECIFIED_VERSION.equals(versionString)) {
                attributes.putIfAbsent("Implementation-Version", versionString);
            }
        }
    }

    private String determineSolonVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return (version != null) ? version : "unknown";
    }

    boolean isZip(File file) {
        try {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                return isZip(fileInputStream);
            }
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean isZip(InputStream inputStream) throws IOException {
        for (byte headerByte : ZIP_FILE_HEADER) {
            if (inputStream.read() != headerByte) {
                return false;
            }
        }
        return true;
    }

    void moveModuleInfoToRoot(CopySpec spec) {
        spec.filesMatching("module-info.class", this::moveToRoot);
    }

    void moveToRoot(FileCopyDetails details) {
        details.setRelativePath(details.getRelativeSourcePath());
    }
}
