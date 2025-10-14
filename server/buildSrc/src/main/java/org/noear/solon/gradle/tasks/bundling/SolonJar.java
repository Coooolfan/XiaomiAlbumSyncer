package org.noear.solon.gradle.tasks.bundling;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class SolonJar extends Jar implements SolonArchive {
    private final SolonArchiveSupport support;

    private final Provider<String> projectName;

    private final Provider<Object> projectVersion;

    private final ResolvedDependencies resolvedDependencies;

    private FileCollection classpath;

    public SolonJar() {
        this.support = new SolonArchiveSupport();
        setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);

        Project project = getProject();
        this.projectName = project.provider(project::getName);
        this.projectVersion = project.provider(project::getVersion);
        CopySpec copySpec = project.copySpec();
        configureBootInfSpec(copySpec);
        getMainSpec().with(copySpec);
        this.resolvedDependencies = new ResolvedDependencies(project);
    }

    @Override
    public void resolvedArtifacts(Provider<Set<ResolvedArtifactResult>> resolvedArtifacts) {
        this.resolvedDependencies.resolvedArtifacts(resolvedArtifacts);
    }

    @Override
    public void copy() {
        this.support.configureManifest(
                getManifest(),
                getMainClass().get(),
                this.getTargetJavaVersion().get().toString(),
                this.projectName.get(),
                this.projectVersion.get()
        );
        super.copy();
    }

    private void configureBootInfSpec(CopySpec bootInfSpec) {
        bootInfSpec.into("", fromCallTo(this::classpathDirectories));
        bootInfSpec.into("", fromCallTo(() -> {
            FileCollection files = getProject().files(this.classpathFiles())
                    .filter(support::isZip);

            List<FileTree> list = new ArrayList<>();
            for (File file : files) {
                list.add(getProject().zipTree(file));
            }
            return list;
        }));

        this.support.moveModuleInfoToRoot(bootInfSpec);
        moveMetaInfToRoot(bootInfSpec);
    }


    private Iterable<File> classpathDirectories() {
        return classpathEntries(File::isDirectory);
    }

    private Iterable<File> classpathFiles() {
        return classpathEntries(File::isFile);
    }

    private Iterable<File> classpathEntries(Spec<File> filter) {
        return (this.classpath != null) ? this.classpath.filter(filter) : Collections.emptyList();
    }

    @Override
    public FileCollection getClasspath() {
        return this.classpath;
    }

    @Override
    public void classpath(Object... classpath) {
        FileCollection existingClasspath = this.classpath;
        this.classpath = getProject().files((existingClasspath != null) ? existingClasspath : Collections.emptyList(), classpath);
    }

    @Override
    public void setClasspath(Object classpath) {
        this.classpath = getProject().files(classpath);
    }

    @Override
    public void setClasspath(FileCollection classpath) {
        this.classpath = getProject().files(classpath);
    }

    private void moveMetaInfToRoot(CopySpec spec) {
        spec.eachFile((file) -> {
            String path = file.getRelativeSourcePath().getPathString();
            if (path.startsWith("META-INF/") && !path.equals("META-INF/aop.xml") && !path.endsWith(".kotlin_module")
                    && !path.startsWith("META-INF/services/")) {
                this.support.moveToRoot(file);
            }
        });
    }

    /**
     * Syntactic sugar that makes {@link CopySpec#into} calls a little easier to read.
     *
     * @param <T>      the result type
     * @param callable the callable
     * @return an action to add the callable to the spec
     */
    private static <T> Action<CopySpec> fromCallTo(Callable<T> callable) {
        return (spec) -> spec.from(callTo(callable));
    }

    /**
     * Syntactic sugar that makes {@link CopySpec#from} calls a little easier to read.
     *
     * @param <T>      the result type
     * @param callable the callable
     * @return the callable
     */
    private static <T> Callable<T> callTo(Callable<T> callable) {
        return callable;
    }
}
