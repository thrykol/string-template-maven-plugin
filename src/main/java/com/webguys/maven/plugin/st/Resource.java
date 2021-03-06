/*
 * The MIT License
 *
 * Copyright 2013 The WebGuys.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.webguys.maven.plugin.st;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Set;
import org.apache.maven.ProjectDependenciesResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.stringtemplate.v4.STGroup;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 * @author thrykol
 */
public class Resource {
    /**
     * The name of the class to instantiate.
     *
     * @parameter
     * @required
     */
    private String className;

		/**
		 * @parameter
		 * @required
		 */
		private File fileName;

		/**
		 * @parameter
		 * @required
		 */
    private File outputDirectory;

		/**
		 * @parameter
		 * @required
		 */
		private File templateDirectory;

    /**
     * Should the this controller attempt to be compiled?
     *
     * @parameter default-value="true"
     */
    private boolean compile = true;

    /**
     * @parameter default-value="1.6"
     */
    private String sourceVersion = "1.6";

    /**
     * @parameter default-value="1.6"
     */
    private String targetVersion = "1.6";

    /**
     * @parameter default-value="3.0"
     */
    private String compilerVersion = "3.0";

    public void invoke(STGroup group, ExecutionEnvironment executionEnvironment, ProjectDependenciesResolver dependenciesResolver, Log log) throws MojoExecutionException
    {
        try
        {
            Class controllerClass = this.findControllerClass(dependenciesResolver, executionEnvironment, log);
						Processor processor = (Processor) controllerClass.newInstance();
						processor.setOutputDirectory(outputDirectory);
						processor.setResourceFile(fileName);
						processor.setLog(log);
						processor.setStringTemplateGroup(group);

						processor.process();
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(String.format("Unable to invoke controller: %s (%s)", this.className, e.getMessage()), e);
        }
    }

    public File getTemplateDirectory()
		{
      return this.templateDirectory;
		}

    private Class findControllerClass(ProjectDependenciesResolver dependenciesResolver, ExecutionEnvironment executionEnvironment, Log log)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, ArtifactResolutionException, ArtifactNotFoundException
    {
        try
        {
            return this.loadController(executionEnvironment.getMavenProject(), executionEnvironment.getMavenSession(), dependenciesResolver);
        }
        catch(ClassNotFoundException e)
        {
            if(this.compile)
            {
                log.info(String.format("Unable to find the class: %s.  Attempting to compile it...", this.className));
                return this.compileAndLoadController(log, dependenciesResolver, executionEnvironment);
            }
            else
            {
                throw new MojoExecutionException(String.format("The class %s is not in the classpath, and compilation is not enabled.", this.className), e);
            }
        }
    }

    private Class compileAndLoadController(Log log, ProjectDependenciesResolver dependenciesResolver, ExecutionEnvironment executionEnvironment)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, ArtifactResolutionException, ArtifactNotFoundException
    {
        MavenProject project = executionEnvironment.getMavenProject();

        Set<Artifact> originalArtifacts = this.configureArtifacts(project);
        this.executeCompilerPlugin(executionEnvironment, log);
        Class result = this.loadController(project, executionEnvironment.getMavenSession(), dependenciesResolver);
        project.setArtifacts(originalArtifacts);
        return result;
    }

    private Set<Artifact> configureArtifacts(MavenProject project)
    {
        Set<Artifact> originalArtifacts = project.getArtifacts();
        project.setArtifacts(project.getDependencyArtifacts());
        return originalArtifacts;
    }

    private void executeCompilerPlugin(ExecutionEnvironment executionEnvironment, Log log) throws MojoExecutionException
    {
        String path = this.className.replace(".", File.separator) + ".java";
        log.info(String.format("Compiling %s...", path));

        executeMojo(
            plugin(
                groupId("org.apache.maven.plugins"),
                artifactId("maven-compiler-plugin"),
                version(compilerVersion)
            ),
            goal("compile"),
            configuration(
                element(name("source"), sourceVersion),
                element(name("target"), targetVersion),
                element(name("includes"), element("include", path))
            ),
            executionEnvironment
        );
    }

    private Class loadController(MavenProject project, MavenSession session, ProjectDependenciesResolver dependenciesResolver)
        throws MalformedURLException, ClassNotFoundException, ArtifactResolutionException, ArtifactNotFoundException
    {
        ArrayList<String> scopes = new ArrayList<String>(1);
        scopes.add(JavaScopes.RUNTIME);
        Set<Artifact> artifacts = dependenciesResolver.resolve(project, scopes, session);

        ArrayList<URL> urls = new ArrayList<URL>();
        urls.add(new File(project.getBuild().getOutputDirectory()).getAbsoluteFile().toURI().toURL());
        for(Artifact artifact : artifacts)
        {
            urls.add(artifact.getFile().getAbsoluteFile().toURI().toURL());
        }

        ClassLoader loader = URLClassLoader.newInstance(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
        return loader.loadClass(this.className);
    }
}
