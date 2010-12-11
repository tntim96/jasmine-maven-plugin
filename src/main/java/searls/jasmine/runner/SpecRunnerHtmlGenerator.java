package searls.jasmine.runner;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.apache.maven.artifact.Artifact;

import searls.jasmine.io.FileUtilsWrapper;
import searls.jasmine.io.IOUtilsWrapper;

public class SpecRunnerHtmlGenerator {

	public static final String RUNNER_HTML_TEMPLATE_FILE = "/templates/SpecRunner.html";

	private static final String CSS_DEPENDENCIES_TEMPLATE_ATTR_NAME = "cssDependencies";
	private static final String JAVASCRIPT_DEPENDENCIES_TEMPLATE_ATTR_NAME = "javascriptDependencies";
	private static final String SOURCES_TEMPLATE_ATTR_NAME = "sources";
	private static final String REPORTER_ATTR_NAME = "reporter";

	private static final String JAVASCRIPT_TYPE = "js";
	private static final String CSS_TYPE = "css";
	
	private FileUtilsWrapper fileUtilsWrapper = new FileUtilsWrapper();
	private IOUtilsWrapper ioUtilsWrapper = new IOUtilsWrapper();

	private File sourceDir;
	private File specDir;
	private List<String> sourcesToLoadFirst;
	private List<File> fileNamesAlreadyWrittenAsScriptTags = new ArrayList<File>();

	public SpecRunnerHtmlGenerator(List<String> sourcesToLoadFirst, File sourceDir, File specDir) {
		this.sourcesToLoadFirst = sourcesToLoadFirst;
		this.sourceDir = sourceDir;
		this.specDir = specDir;
	}

	public String generate(List<Artifact> dependencies, ReporterType reporterType, File customRunnerTemplate) {
		try {
			String htmlTemplate = resolveHtmlTemplate(customRunnerTemplate);
			StringTemplate template = new StringTemplate(htmlTemplate, DefaultTemplateLexer.class);

			includeJavaScriptAndCssDependencies(dependencies, template);
			setJavaScriptSourcesAttribute(template);
			template.setAttribute(REPORTER_ATTR_NAME, reporterType.name());

			return template.toString();
		} catch (IOException e) {
			throw new RuntimeException("Failed to load file names for dependencies or scripts", e);
		}
	}

	private String resolveHtmlTemplate(File customRunnerTemplate) throws IOException {
		return customRunnerTemplate != null ? 
				fileUtilsWrapper.readFileToString(customRunnerTemplate) 
				: ioUtilsWrapper.toString(getClass().getResourceAsStream(RUNNER_HTML_TEMPLATE_FILE));
	}

	private void includeJavaScriptAndCssDependencies(List<Artifact> dependencies, StringTemplate template) throws IOException {
		StringBuilder javaScriptDependencies = new StringBuilder();
		StringBuilder cssDependencies = new StringBuilder();
		for (Artifact dep : dependencies) {
			if (JAVASCRIPT_TYPE.equals(dep.getType())) {
				javaScriptDependencies.append("<script type=\"text/javascript\">").append(fileUtilsWrapper.readFileToString(dep.getFile())).append("</script>");
			} else if (CSS_TYPE.equals(dep.getType())) {
				cssDependencies.append("<style type=\"text/css\">").append(fileUtilsWrapper.readFileToString(dep.getFile())).append("</style>");
			}
		}
		template.setAttribute(JAVASCRIPT_DEPENDENCIES_TEMPLATE_ATTR_NAME, javaScriptDependencies.toString());
		template.setAttribute(CSS_DEPENDENCIES_TEMPLATE_ATTR_NAME, cssDependencies.toString());
	}

	private void setJavaScriptSourcesAttribute(StringTemplate template) throws IOException {
		StringBuilder scriptTags = new StringBuilder();
		appendScriptTagsForFiles(scriptTags, expandSourcesToLoadFirstRelativeToSourceDir());
		appendScriptTagsForFiles(scriptTags, filesForScriptsInDirectory(sourceDir));
		appendScriptTagsForFiles(scriptTags, filesForScriptsInDirectory(specDir));
		template.setAttribute(SOURCES_TEMPLATE_ATTR_NAME, scriptTags.toString());
	}

	private List<File> expandSourcesToLoadFirstRelativeToSourceDir() {
		List<File> files = new ArrayList<File>();
		if (sourcesToLoadFirst != null) {
			for (String sourceToLoadFirst : sourcesToLoadFirst) {
				files.add(new File(sourceDir, sourceToLoadFirst));
			}
		}
		return files;
	}

	private List<File> filesForScriptsInDirectory(File directory) throws IOException {
		List<File> files = new ArrayList<File>();
		if (directory != null) {
			fileUtilsWrapper.forceMkdir(directory);
			files = new ArrayList<File>(fileUtilsWrapper.listFiles(directory, new String[] { "js" }, true));
			Collections.sort(files);
		}
		return files;
	}

	private void appendScriptTagsForFiles(StringBuilder sb, List<File> sourceFiles) throws MalformedURLException {
		for (File sourceFile : sourceFiles) {
			if (!fileNamesAlreadyWrittenAsScriptTags.contains(sourceFile)) {
				sb.append("<script type=\"text/javascript\" src=\"").append(sourceFile.toURI().toURL().toString()).append("\"></script>");
				fileNamesAlreadyWrittenAsScriptTags.add(sourceFile);
			}
		}
	}

}
