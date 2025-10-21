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

package org.noear.solon.gradle.tasks.run;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.work.DisableCachingByDefault;

/**
 * Custom {@link JavaExec} task for running a Solon application.
 *
 * @author Andy Wilkinson
 */
@DisableCachingByDefault(because = "Application should always run")
public abstract class SolonRun extends JavaExec {

    public SolonRun() {
        getOptimizedLaunch().convention(true);
    }

    /**
     * Returns the property for whether the JVM's launch should be optimized. The property
     * defaults to {@code true}.
     *
     * @return whether the JVM's launch should be optimized
     */
    @Input
    public abstract Property<Boolean> getOptimizedLaunch();

    @Override
    public void exec() {
        if (getOptimizedLaunch().get()) {
            setJvmArgs(getJvmArgs());
            jvmArgs("-XX:TieredStopAtLevel=1");
        }
        super.exec();
    }

}
