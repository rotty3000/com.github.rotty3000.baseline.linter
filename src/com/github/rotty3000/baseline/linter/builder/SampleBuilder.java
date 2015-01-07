package com.github.rotty3000.baseline.linter.builder;

import aQute.bnd.differ.Baseline;
import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.osgi.EmbeddedResource;
import aQute.bnd.osgi.Instruction;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.service.diff.Delta;
import aQute.bnd.service.diff.Diff;
import aQute.bnd.service.diff.Type;
import aQute.bnd.version.Version;

import aQute.lib.io.IO;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.LogOptions;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.osgi.framework.Constants;

public class SampleBuilder extends IncrementalProjectBuilder {

	public SampleBuilder() throws IOException, ParseException {
		baseline = new Baseline(new Processor(), new DiffPluginImpl());

		ivy = Ivy.newInstance();
		ivy.configureDefault();

		DependencyResolver localM2Resolver = localM2Resolver();

		IvySettings ivySettings = ivy.getSettings();
		ivySettings.addConfigured(localM2Resolver);

		DependencyResolver resolver = ivySettings.getResolver("default");

		if (resolver instanceof ChainResolver) {
			ChainResolver chainResolver = (ChainResolver)resolver;

			@SuppressWarnings({"unchecked"})
			List<DependencyResolver> resolvers = chainResolver.getResolvers();

			resolvers.add(0, localM2Resolver);
		}
	}

	void checkBaseline(IResource resource) {
		if (!(resource instanceof IFile)) {
			return;
		}

		IFile file = (IFile)resource;

		IJavaElement packageElement = JavaCore.create(file.getParent());

		if ((packageElement == null) ||
			!(packageElement instanceof IPackageFragment)) {

			return;
		}

		IPackageFragment packageFragment = (IPackageFragment)packageElement;

		if (processedPackages.contains(packageFragment.getElementName())) {
			return;
		}

		diffMarker.deleteMarkers(file);

		processedPackages.add(packageFragment.getElementName());

		baseline(file, packageFragment, createJar(packageFragment));
	}

	protected void addManifest(
			Jar jar, IPackageFragment packageFragment, Version version)
		throws UnsupportedEncodingException {

		StringBuilder sb = new StringBuilder();

		sb.append("Manifest-Version: 1.0\n");
		sb.append(Constants.BUNDLE_MANIFESTVERSION);
		sb.append(": 2\n");
		sb.append(Constants.BUNDLE_SYMBOLICNAME);
		sb.append(": ");
		sb.append(getMvnArtifact(getProject()));
		sb.append("\n");
		sb.append(Constants.EXPORT_PACKAGE);
		sb.append(": ");
		sb.append(packageFragment.getElementName());
		sb.append(";version=");
		sb.append(version);
		sb.append("\n");

		Resource bndResource = new EmbeddedResource(
			sb.toString().getBytes("UTF-8"), System.currentTimeMillis());

		jar.putResource("META-INF/MANIFEST.MF", bndResource);
	}

	protected void baseline(
		IFile file, IPackageFragment packageFragment, Jar newer) {

		if (newer == null) {
			return;
		}

		try {
			Instructions packageFilters = new Instructions();

			packageFilters.put(
				new Instruction(packageFragment.getElementName()), null);

			Set<Info> infos = baseline.baseline(newer, older, packageFilters);

			processInfos(file, infos);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			newer.close();
		}
	}

	@Override
	protected IProject[] build(
			int kind, @SuppressWarnings("rawtypes") Map args,
			IProgressMonitor monitor)
		throws CoreException {

		IProject project = getProject();

		if (!init(project)) {
			return null;
		}

		if (kind == FULL_BUILD) {
			fullBuild(project, monitor);
		}
		else {
			IResourceDelta delta = getDelta(project);

			if (delta == null) {
				fullBuild(project, monitor);
			}
			else {
				incrementalBuild(delta, monitor);
			}
		}

		processedPackages.clear();

		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		if (diffMarker == null) {
			diffMarker = new DiffMarker(null);
		}

		diffMarker.deleteMarkers(getProject());
		processedPackages.clear();
		sourceToOutputMap.clear();

		buildProperties = null;
		diffMarker = null;
		javaProject = null;
		older = null;
	}

	protected Jar createJar(IPackageFragment packageFragment) {
		Jar jar = new Jar("dot");

		try {
			for (IJavaElement javaElement : packageFragment.getChildren()) {
				diffMarker.deleteMarkers((IFile)javaElement.getResource());
			}

			addManifest(jar, packageFragment, collectVersion(packageFragment));

			IPath path = packageFragment.getPath();
			IPath[] sourceAndOutput = getSourceAndOutput(path);
			IPath packageOutputFolder = sourceAndOutput[1].append(
				path.removeFirstSegments(
					sourceAndOutput[0].segments().length));

			IFolder folder = (IFolder)workspaceRoot.findMember(packageOutputFolder);

			for (IResource resource : folder.members()) {
				if (!(resource instanceof IFile)) {
					continue;
				}

				IFile curFile = (IFile)resource;

				Resource bndResource = new EmbeddedResource(
					IO.read(curFile.getContents()),
					curFile.getModificationStamp());

				IPath curPath =
					curFile.getProjectRelativePath().removeFirstSegments(1);

				jar.putResource(curPath.toString(), bndResource);
			}

			return jar;
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		jar.close();

		return null;
	}

	private Version collectVersion(IPackageFragment packageFragment)
		throws IOException, CoreException {

		ICompilationUnit packageInfo = packageFragment.getCompilationUnit(
			"package-info.java");

		if (packageInfo.exists()) {
			IPackageDeclaration packageDeclaration =
				(IPackageDeclaration)packageInfo.getChildren()[0];

			IAnnotation[] annotations = packageDeclaration.getAnnotations();

			for (IAnnotation annotation : annotations) {
				String elementName = annotation.getElementName();
				int pos = elementName.lastIndexOf(".") + 1;

				elementName = elementName.substring(pos);

				if (!elementName.equals("Version")) {
					continue;
				}

				return Version.parseVersion(
					(String)annotation.getMemberValuePairs()[0].getValue());
			}

			return Version.LOWEST;
		}

		Object[] nonJavaResources = packageFragment.getNonJavaResources();

		for (Object object : nonJavaResources) {
			if (!(object instanceof IFile) &&
				!((IFile)object).getName().equals("packageinfo")) {

				continue;
			}

			String versionInfo = IO.collect(
				((IFile)object).getContents()).trim();

			if (versionInfo.length() <= 0) {
				return Version.LOWEST;
			}

			return Version.parseVersion(
				versionInfo.substring("version".length()).trim());
		}

		return Version.LOWEST;
	}

	protected void fullBuild(
			IProject project, IProgressMonitor monitor)
		throws CoreException {

		try {
			project.accept(new SampleResourceVisitor());
		}
		catch (CoreException e) {
			//ignore
		}
	}

	protected String getMvnArtifact(IProject project) {
		return buildProperties.getProperty("maven.artifact", project.getName());
	}

	protected String getMvnOrg(IProject project) {
		return buildProperties.getProperty("maven.org", project.getName());
	}

	protected String getMvnVersion(IProject project) {
		return buildProperties.getProperty("maven.version", "latest.release");
	}

	protected IPath[] getSourceAndOutput(IPath compilationUnitPath) {
		for (Entry<IClasspathEntry, IPath> entry :
				sourceToOutputMap.entrySet()) {

			IPath path = entry.getKey().getPath();

			if (compilationUnitPath.matchingFirstSegments(path) ==
					path.segments().length) {

				return new IPath[] {
					path, entry.getValue().addTrailingSeparator()};
			}
		}

		throw new IllegalStateException(
			"Missing source folder " + compilationUnitPath.toString());
	}

	protected void incrementalBuild(
			IResourceDelta delta, IProgressMonitor monitor)
		throws CoreException {

		delta.accept(new SampleDeltaVisitor());
	}

	protected boolean init(IProject project) {
		if (older != null) {
			return true;
		}

		synchronized (this) {
			if (older != null) {
				return true;
			}

			workspaceRoot = project.getWorkspace().getRoot();
			buildProperties = new Properties();

			try {
				IFile file = project.getFile("build.properties");

				if (file.exists()) {
					InputStream stream = file.getContents();

					buildProperties.load(stream);
				}

				ModuleRevisionId moduleRevisionId =
					ModuleRevisionId.newInstance(
						getMvnOrg(project), getMvnArtifact(project),
						getMvnVersion(project));

				ResolveOptions resolveOptions = new ResolveOptions();

				resolveOptions.setConfs(new String[] {"default"});
				resolveOptions.setLog(LogOptions.LOG_QUIET);

				ResolveReport resolveReport = ivy.resolve(
					moduleRevisionId, resolveOptions, true);

				if (resolveReport.hasError()) {
					return false;
				}

				ArtifactDownloadReport artifactDowloadReport =
					resolveReport.getAllArtifactsReports()[0];

				older = new Jar(artifactDowloadReport.getLocalFile());

				setupJavaProject();

				return true;
			}
			catch (Exception e) {
				e.printStackTrace();

				try {
					clean(null);
				}
				catch (CoreException ce) {
					ce.printStackTrace();
				}
			}
		}

		return false;
	}

	protected DependencyResolver localM2Resolver() {
		IBiblioResolver localM2 = new IBiblioResolver();

		localM2.setM2compatible(true);
		localM2.setName("local-m2");
		localM2.setRoot(
			"file://" + System.getProperty("user.home") + "/.m2/repository");

		return localM2;
	}

	protected void processInfos(IFile file, Set<Info> infos) {
		for (Info info : infos) {
			if ((info.suggestedVersion != null) &&
				(info.suggestedVersion.compareTo(info.newerVersion) <= 0) &&
				(info.suggestedVersion.compareTo(Version.LOWEST) > 0)) {

				continue;
			}

			processDiff(file, info.packageDiff, null, info);

			if (info.packageDiff.getDelta() == Delta.REMOVED) {
				continue;
			}

			System.out.print(
				String.format(
					"%s %-50s %-10s %-10s %-10s %-10s %-10s%n",
					info.mismatch ? '*' : ' ',
					info.packageName,
					info.packageDiff.getDelta(),
					info.newerVersion,
					info.olderVersion != null && info.olderVersion.equals(aQute.bnd.version.Version.LOWEST) ? "-": info.olderVersion,
					info.suggestedVersion != null && info.suggestedVersion.compareTo(info.newerVersion) <= 0 ? "ok" : info.suggestedVersion,
					info.suggestedIfProviders == null ? "-" : info.suggestedIfProviders
				)
			);
		}
	}

	protected void processDiff(
		IFile file, Diff diff, Diff parentDiff, Info info) {

		Delta delta = diff.getDelta();

		if (delta == Delta.UNCHANGED) {
			return;
		}

		Type type = diff.getType();

		try {
			if (type == Type.ACCESS) {
				diffMarker.doAccess(file, diff, parentDiff, info);
			}
			else if (type == Type.ANNOTATED) {
				diffMarker.doAnnotated(file, diff, parentDiff, info);
			}
			else if (type == Type.ANNOTATION) {
				diffMarker.doAnnotation(file, diff, parentDiff, info);
			}
			else if (type == Type.CLASS) {
				diffMarker.doClass(file, diff, parentDiff, info);
			}
//			else if (type == Type.CLASS_VERSION) {
//				diffMarker.doClassVersion(file, diff, parentDiff, info);
//			}
			else if (type == Type.CONSTANT) {
				diffMarker.doConstant(file, diff, parentDiff, info);
			}
//			else if (type == Type.DEPRECATED) {
//				diffMarker.doDeprecated(file, diff, parentDiff, info);
//			}
			else if (type == Type.ENUM) {
				diffMarker.doEnum(file, diff, parentDiff, info);
			}
			else if (type == Type.EXTENDS) {
				diffMarker.doExtends(file, diff, parentDiff, info);
			}
			else if (type == Type.FIELD) {
				diffMarker.doField(file, diff, parentDiff, info);
			}
			else if (type == Type.IMPLEMENTS) {
				diffMarker.doImplements(file, diff, parentDiff, info);
			}
			else if (type == Type.INTERFACE) {
				diffMarker.doInterface(file, diff, parentDiff, info);
			}
			else if (type == Type.METHOD) {
				diffMarker.doMethod(file, diff, parentDiff, info);
			}
			else if (type == Type.PACKAGE) {
				diffMarker.doPackage(file, diff, parentDiff, info);
			}
			else if (type == Type.PARAMETER) {
				diffMarker.doParameter(file, diff, parentDiff, info);
			}
			else if (type == Type.PROPERTY) {
				diffMarker.doProperty(file, diff, parentDiff, info);
			}
			else if (type == Type.RETURN) {
				diffMarker.doReturn(file, diff, parentDiff, info);
			}
			else if (type == Type.VERSION) {
				diffMarker.doVersion(file, diff, parentDiff, info);
			}
			else {
				System.out.println(
					String.format("%s %s %s", delta, type, diff.getName()));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		for (Diff curDiff : diff.getChildren()) {
			processDiff(file, curDiff, diff, info);
		}
	}

	protected void setupJavaProject() throws JavaModelException {
		javaProject = JavaCore.create(getProject());
		diffMarker = new DiffMarker(javaProject);

		IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();

		for (IClasspathEntry classpathEntry : classpathEntries) {
			if (classpathEntry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				continue;
			}

			IPath outputPath = javaProject.getOutputLocation();

			if (classpathEntry.getOutputLocation() != null) {
				outputPath = classpathEntry.getOutputLocation();
			}

			sourceToOutputMap.put(classpathEntry, outputPath);
		}
	}

	private final Baseline baseline;
	private Properties buildProperties;
	private DiffMarker diffMarker;
	private final Ivy ivy;
	private IJavaProject javaProject;
	private Jar older;
	private List<String> processedPackages = new ArrayList<>();
	private IWorkspaceRoot workspaceRoot;
	private Map<IClasspathEntry, IPath> sourceToOutputMap =
		new ConcurrentHashMap<>();

	class SampleDeltaVisitor implements IResourceDeltaVisitor {

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();

			switch (delta.getKind()) {
				case IResourceDelta.ADDED: {
					checkBaseline(resource);
					break;
				}
				case IResourceDelta.REMOVED: {
					break;
				}
				case IResourceDelta.CHANGED: {
					checkBaseline(resource);
					break;
				}
			}

			return true;
		}

	}

	class SampleResourceVisitor	implements IResourceVisitor {

		@Override
		public boolean visit(IResource resource) {
			checkBaseline(resource);

			return true;
		}

	}

}