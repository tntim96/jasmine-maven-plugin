package com.github.searls.jasmine.mojo;

import com.github.searls.jasmine.config.JasmineConfiguration;
import com.github.searls.jasmine.exception.StringifiesStackTraces;
import com.github.searls.jasmine.io.ScansDirectory;
import com.github.searls.jasmine.model.FileSystemReporter;
import com.github.searls.jasmine.model.Reporter;
import com.github.searls.jasmine.model.ScriptSearch;
import com.github.searls.jasmine.runner.SpecRunnerTemplate;
import com.github.searls.jasmine.thirdpartylibs.ProjectClassLoaderFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.resource.ResourceManager;
import org.eclipse.jetty.server.Connector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractJasmineMojo extends AbstractMojo implements JasmineConfiguration {

  // Properties in order of most-to-least interesting for client projects to override

  /**
   * Directory storing your JavaScript.
   *
   * @since 1.1.0
   */
  @Parameter(
    property = "jsSrcDir",
    defaultValue = "${project.basedir}${file.separator}src${file.separator}main${file.separator}javascript")
  private File jsSrcDir;

  /**
   * Directory storing your Jasmine Specs.
   *
   * @since 1.1.0
   */
  @Parameter(
    property = "jsTestSrcDir",
    defaultValue = "${project.basedir}${file.separator}src${file.separator}test${file.separator}javascript")
  private File jsTestSrcDir;

  /**
   * <p>JavaScript sources (typically vendor/lib dependencies) that need to be loaded
   * before other sources (and specs) in a particular order. Each source will first be
   * searched for relative to <code>${jsSrcDir}</code>, then <code>${jsTestSrcDir}</code>,
   * then (if it's not found in either) it will be included exactly as it appears in your POM.</p>
   * <br>
   * <p>Therefore, if jquery.js is in <code>${jsSrcDir}/vendor</code>, you would configure:</p>
   * <pre>
   * &lt;preloadSources&gt;
   *   &lt;source&gt;vendor/jquery.js&lt;/source&gt;
   * &lt;/preloadSources&gt;
   * </pre>
   * <br>
   * <p>And jquery.js would load before all the other sources and specs.</p>
   *
   * @since 1.1.0
   */
  @Parameter
  protected List<String> preloadSources;

  /**
   * <p>It may be the case that the jasmine-maven-plugin doesn't currently suit all of your needs,
   * and as a result the generated SpecRunner HTML files are set up in a way that you can't run
   * your specs. Have no fear! Simply specify a custom spec runner template in the plugin configuration
   * and make the changes you need.</p>
   * <br>
   * <p>Potential values are a filesystem path, a URL, or a classpath resource. The default template is
   * stored in <code>src/main/resources/jasmine-templates/SpecRunner.htmltemplate</code>, and the
   * required template strings are tokenized in "$*$" patterns.</p>
   * <br>
   * <p>Example usage:</p>
   * <pre>
   * &lt;customRunnerTemplate&gt;${project.basedir}/src/test/resources/myCustomRunner.template&lt;/customRunnerTemplate&gt;
   * </pre>
   *
   * @since 1.1.0
   */
  @Parameter
  protected String customRunnerTemplate;

  /**
   * <p>Sometimes you want to have full control over how scriptloaders are configured. In order to
   * interpolate custom configuration into the generated runnerTemplate, specify a file containing
   * the additional config. Potential values are a filesystem path, a URL, or a classpath resource.</p>
   * <br>
   * <p>Example usage:</p>
   * <pre>
   * &lt;customRunnerConfiguration&gt;${project.basedir}/src/test/resources/myCustomConfig.txt&lt;/customRunnerConfiguration&gt;
   * </pre>
   *
   * @since 1.1.0
   */
  @Parameter
  protected String customRunnerConfiguration;

  /**
   * <p> Specify a custom reporter to be used to print the test report.</p>
   * <p>Example usage:</p>
   * <pre>
   * &lt;reporters&gt;
   *   &lt;reporter&gt;
   *     &lt;reporterName&gt;${project.basedir}/src/test/resources/myCustomReporter.js&lt;/reporterName&gt;
   *   &lt;/reporter&gt;
   *   &lt;reporter&gt;
   *     &lt;reporterName&gt;STANDARD&lt;/reporterName&gt;
   *   &lt;/reporter&gt;
   * &lt;/reporters&gt;
   * </pre>
   */
  @Parameter
  protected List<Reporter> reporters = new ArrayList<Reporter>();

  /**
   * <p> Specify a custom file system reporter to be used to store the test report.</p>
   * <p>Example usage:</p>
   * <pre>
   * &lt;fileSystemReporters&gt;
   *   &lt;reporter&gt;
   *     &lt;fileName&gt;MyFile.log&lt;/fileName&gt;
   *     &lt;reporterName&gt;${project.basedir}/src/test/resources/myCustomReporter.js&lt;/reporterName&gt;
   *   &lt;/reporter&gt;
   *   &lt;reporter&gt;
   *     &lt;fileName&gt;Test-jasmine.xml&lt;/fileName&gt;
   *     &lt;reporterName&gt;JUNIT_XML&lt;/reporterName&gt;
   *   &lt;/reporter&gt;
   * &lt;/fileSystemReporters&gt;
   * </pre>
   */
  @Parameter
  protected List<FileSystemReporter> fileSystemReporters = new ArrayList<FileSystemReporter>();

  /**
   * Target directory for files created by the plugin.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "${project.build.directory}${file.separator}jasmine")
  protected File jasmineTargetDir;

  /**
   * Target directory for coverage files created by the plugin.
   *
   * @since 2.3.0
   */
  @Parameter(defaultValue = "${project.build.directory}${file.separator}coverage")
  protected File coverageTargetDir;

  /**
   * Skip execution of tests.
   *
   * @see <a href="http://maven.apache.org/general.html#skip-test">http://maven.apache.org/general.html#skip-test</a>
   * @since 1.1.0
   */
  @Parameter(property = "skipTests")
  protected boolean skipTests;

  /**
   * Skip compilation and execution of tests.
   *
   * @see <a href="http://maven.apache.org/general.html#skip-test">http://maven.apache.org/general.html#skip-test</a>
   * @since 1.3.1.3
   */
  @Parameter(property = "maven.test.skip")
  protected boolean mvnTestSkip;

  /**
   * Skip only jasmine tests
   *
   * @since 1.3.1.3
   */
  @Parameter(property = "skipJasmineTests")
  protected boolean skipJasmineTests;

  /**
   * Halt the build on test failure.
   *
   * @since 1.1.0
   */
  @Parameter(property = "haltOnFailure", defaultValue = "true")
  protected boolean haltOnFailure;

  /**
   * Timeout for spec execution in seconds.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "300")
  protected int timeout;

  /**
   * True to increase HtmlUnit output and attempt reporting on specs even if a timeout occurred.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "false")
  protected boolean debug;

  /**
   * The name of the Spec Runner file.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "SpecRunner.html")
  protected String specRunnerHtmlFileName;

  /**
   * The name of the Manual Spec Runner.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "ManualSpecRunner.html")
  protected String manualSpecRunnerHtmlFileName;

  /**
   * The name of the directory the specs will be deployed to on the server.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "spec")
  protected String specDirectoryName;

  /**
   * The name of the directory the sources will be deployed to on the server.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "src")
  protected String srcDirectoryName;

  /**
   * The source encoding.
   *
   * @since 1.1.0
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}")
  protected String sourceEncoding;

  /**
   * <p>Allows specifying which source files should be included and in what order.</p>
   * <pre>
   * &lt;sourceIncludes&gt;
   *   &lt;include&gt;vendor/&#42;&#42;/&#42;.js&lt;/include&gt;
   *   &lt;include&gt;myBootstrapFile.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.coffee&lt;/include&gt;
   * &lt;/sourceIncludes&gt;
   * </pre>
   * <br>
   * <p>Default <code>sourceIncludes</code>:</p>
   * <pre>
   * &lt;sourceIncludes&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.coffee&lt;/include&gt;
   * &lt;/sourceIncludes&gt;
   * </pre>
   *
   * @since 1.1.0
   */
  @Parameter
  private final List<String> sourceIncludes = ScansDirectory.DEFAULT_INCLUDES;

  /**
   * <p>Just like <code>sourceIncludes</code>, but will exclude anything matching the provided patterns.</p>
   * <p>There are no <code>sourceExcludes</code> by default.</p>
   *
   * @since 1.1.0
   */
  @Parameter
  private final List<String> sourceExcludes = Collections.emptyList();

  /**
   * <p>I often find myself needing control of the spec include order
   * when I have some global spec helpers or spec-scoped dependencies, like:</p>
   * <pre>
   * &lt;specIncludes&gt;
   *   &lt;include&gt;jasmine-jquery.js&lt;/include&gt;
   *   &lt;include&gt;spec-helper.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.coffee&lt;/include&gt;
   * &lt;/specIncludes&gt;
   * </pre>
   * <br>
   * <p>Default <code>specIncludes</code>:</p>
   * <pre>
   * &lt;specIncludes&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.js&lt;/include&gt;
   *   &lt;include&gt;&#42;&#42;/&#42;.coffee&lt;/include&gt;
   * &lt;/specIncludes&gt;
   * </pre>
   *
   * @since 1.1.0
   */
  @Parameter
  private final List<String> specIncludes = ScansDirectory.DEFAULT_INCLUDES;

  /**
   * <p>Just like <code>specIncludes</code>, but will exclude anything matching the provided patterns.</p>
   * <p>There are no <code>specExcludes</code> by default.</p>
   *
   * @since 1.1.0
   */
  @Parameter
  private final List<String> specExcludes = Collections.emptyList();

  /**
   * <p>Used by the <code>jasmine:bdd</code> goal to specify port to run the server under.</p>
   * <br>
   * <p>The <code>jasmine:test</code> goal always uses a random available port so this property is ignored.</p>
   *
   * @since 1.1.0
   */
  @Parameter(property = "jasmine.serverPort", defaultValue = "8234")
  protected int serverPort;

  /**
   * <p>Specify the URI scheme in which to access the SpecRunner.</p>
   *
   * @since 1.3.1.4
   */
  @Parameter(property = "jasmine.uriScheme", defaultValue = "http")
  protected String uriScheme;

  /**
   * <p>Not used by the <code>jasmine:bdd</code> goal.</p>
   * <br>
   * <p>The <code>jasmine:test</code> goal to specify hostname where the server is running.  Useful when using
   * the RemoteWebDriver.</p>
   *
   * @since 1.3.1.4
   */
  @Parameter(property = "jasmine.serverHostname", defaultValue = "localhost")
  protected String serverHostname;

  /**
   * <p>Determines the strategy to use when generation the JasmineSpecRunner. This feature allows for custom
   * implementation of the runner generator. Typically this is used when using different script runners.</p>
   * <br>
   * <p>Some valid examples: DEFAULT, REQUIRE_JS</p>
   *
   * @since 1.1.0
   */
  @Parameter(property = "jasmine.specRunnerTemplate", defaultValue = "DEFAULT")
  protected SpecRunnerTemplate specRunnerTemplate;

  /**
   * <p>Automatically refresh the test runner at the given interval (specified in seconds) when using the <code>jasmine:bdd</code> goal.</p>
   * <p>A value of <code>0</code> disables the automatic refresh (which is the default).</p>
   *
   * @since 1.3.1.1
   */
  @Parameter(property = "jasmine.autoRefreshInterval", defaultValue = "0")
  protected int autoRefreshInterval;

  /**
   * <p>Control the Coffee Script compilation.  e.g. When using RequireJS the compilation
   * happens within the Coffee Script AMD loader plugin; we therefore need to disable the
   * compilation here.</p>
   *
   * @since 1.3.1.4
   */
  @Parameter(property = "coffeeScriptCompilationEnabled", defaultValue = "true")
  protected boolean coffeeScriptCompilationEnabled;

  /**
   * <p>Type of {@link org.eclipse.jetty.server.Connector} to use on the jetty server.</p>
   * <br>
   * <p>Most users won't need to change this from the default value. It should only be used
   * by advanced users.</p>
   *
   * @since 1.3.1.4
   */
  @Parameter(
    property = "jasmine.connectorClass",
    defaultValue = "org.eclipse.jetty.server.nio.SelectChannelConnector"
  )
  protected String connectorClass;

  /**
   * <p>Specify additional contexts to make available.</p>
   * <pre>
   * &lt;additionalContexts&gt;
   *   &lt;context&gt;
   *     &lt;contextRoot&gt;lib&lt;/contextRoot&gt;
   *     &lt;directory&gt;${project.basedir}/src/main/lib&lt;/directory&gt;
   *   &lt;/context&gt;
   *   &lt;context&gt;
   *     &lt;contextRoot&gt;test/lib&lt;/contextRoot&gt;
   *     &lt;directory&gt;${project.basedir}/src/test/lib&lt;/directory&gt;
   *   &lt;/context&gt;
   * &lt;/additionalContexts&gt;
   * </pre>
   *
   * @since 1.3.1.5
   */
  @Parameter
  private List<Context> additionalContexts = Collections.emptyList();

  @Parameter(defaultValue = "${project}", readonly = true)
  protected MavenProject mavenProject;

  @Component
  ResourceManager resourceManager;

  protected ResourceRetriever resourceRetriever;
  protected ReporterRetriever reporterRetriever;

  protected StringifiesStackTraces stringifiesStackTraces = new StringifiesStackTraces();

  protected ScriptSearch sources;
  protected ScriptSearch specs;

  private File customRunnerTemplateFile;
  private File customRunnerConfigurationFile;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    this.loadResources();

    this.sources = new ScriptSearch(this.jsSrcDir, this.sourceIncludes, this.sourceExcludes);
    this.specs = new ScriptSearch(this.jsTestSrcDir, this.specIncludes, this.specExcludes);

    try {
      this.run();
    } catch (MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException("The jasmine-maven-plugin encountered an exception: \n" + this.stringifiesStackTraces.stringify(e), e);
    }
  }

  public abstract void run() throws Exception;

  @Override
  public String getSourceEncoding() {
    return this.sourceEncoding;
  }

  @Override
  public File getCustomRunnerTemplate() {
    return this.customRunnerTemplateFile;
  }

  @Override
  public SpecRunnerTemplate getSpecRunnerTemplate() {
    return this.specRunnerTemplate;
  }

  @Override
  public File getJasmineTargetDir() {
    return this.jasmineTargetDir;
  }

  @Override
  public String getSrcDirectoryName() {
    return this.srcDirectoryName;
  }

  @Override
  public ScriptSearch getSources() {
    return this.sources;
  }

  @Override
  public ScriptSearch getSpecs() {
    return this.specs;
  }

  @Override
  public String getSpecDirectoryName() {
    return this.specDirectoryName;
  }

  @Override
  public List<String> getPreloadSources() {
    return this.preloadSources;
  }

  @Override
  public int getAutoRefreshInterval() {
    return this.autoRefreshInterval;
  }

  @Override
  public boolean isCoffeeScriptCompilationEnabled() {
    return this.coffeeScriptCompilationEnabled;
  }

  public MavenProject getMavenProject() {
    return this.mavenProject;
  }

  @Override
  public File getCustomRunnerConfiguration() {
    return this.customRunnerConfigurationFile;
  }

  @Override
  public List<Reporter> getReporters() {
    return this.reporters;
  }

  @Override
  public List<FileSystemReporter> getFileSystemReporters() {
    return this.fileSystemReporters;
  }

  @Override
  public File getBasedir() {
    return this.mavenProject.getBasedir();
  }

  @Override
  public ClassLoader getProjectClassLoader() {
    return new ProjectClassLoaderFactory(mavenProject.getArtifacts()).create();
  }

  @Override
  public List<Context> getContexts() {
    List<Context> contexts = new ArrayList<Context>();
    contexts.add(new Context(this.srcDirectoryName, this.jsSrcDir));
    contexts.add(new Context(this.specDirectoryName, this.jsTestSrcDir));
    contexts.addAll(additionalContexts);
    return contexts;
  }

  protected Connector getConnector() throws MojoExecutionException {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends Connector> c = (Class<? extends Connector>) Class.forName(connectorClass);
      return c.newInstance();
    } catch (InstantiationException e) {
      throw new MojoExecutionException("Unable to instantiate.", e);
    } catch (IllegalAccessException e) {
      throw new MojoExecutionException("Unable to instantiate.", e);
    } catch (ClassNotFoundException e) {
      throw new MojoExecutionException("Unable to instantiate.", e);
    }
  }

  protected boolean isSkipTests() {
    return this.skipTests || this.mvnTestSkip || this.skipJasmineTests;
  }

  private void loadResources() throws MojoExecutionException {
    this.customRunnerTemplateFile = getResourceRetriever().getResourceAsFile("customRunnerTemplate", this.customRunnerTemplate, this.mavenProject);
    this.customRunnerConfigurationFile = getResourceRetriever().getResourceAsFile("customRunnerConfiguration", this.customRunnerConfiguration, this.mavenProject);
    this.reporters = getReporterRetriever().retrieveReporters(this.reporters, this.mavenProject);
    this.fileSystemReporters = getReporterRetriever().retrieveFileSystemReporters(this.fileSystemReporters, this.getJasmineTargetDir(), this.mavenProject);
  }

  private ResourceRetriever getResourceRetriever() {
    if (resourceRetriever == null) {
      resourceRetriever = new ResourceRetriever(resourceManager);
    }
    return resourceRetriever;
  }

  private ReporterRetriever getReporterRetriever() {
    if (reporterRetriever == null) {
      reporterRetriever = new ReporterRetriever(getResourceRetriever());
    }
    return reporterRetriever;
  }
}
