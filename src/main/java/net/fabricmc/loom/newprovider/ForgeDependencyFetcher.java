package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DownloadSession;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public class ForgeDependencyFetcher extends NewProvider<ForgeDependencyFetcher> {
	public ForgeDependencyFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private ResolvedConfigElementWrapper forge;
	private String fmlLibrariesBaseUrl;
	
	public ForgeDependencyFetcher forge(ResolvedConfigElementWrapper forge) {
		this.forge = forge;
		return this;
	}
	
	public ForgeDependencyFetcher fmlLibrariesBaseUrl(String fmlLibrariesBaseUrl) {
		this.fmlLibrariesBaseUrl = fmlLibrariesBaseUrl;
		return this;
	}
	
	//outputs
	private final Collection<String> sniffedLibraries = new ArrayList<>();
	private Collection<Path> resolvedLibraries = new ArrayList<>();
	
	//procedure
	public ForgeDependencyFetcher sniff() throws Exception {
		class LibrarySniffingClassVisitor extends ClassVisitor {
			public LibrarySniffingClassVisitor(ClassVisitor classVisitor, Collection<String> sniffedLibraries) {
				super(Opcodes.ASM4, classVisitor);
				this.sniffedLibraries = sniffedLibraries;
			}
			
			private final Collection<String> sniffedLibraries;
			
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				//static initializer
				if(name.equals("<clinit>")) return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					@Override
					public void visitLdcInsn(Object value) {
						//This method ldcs library filenames ("argo-2.25.jar") and also their SHA1 hashes as strings.
						//I differentiate between them by just looking for the .jar suffix, but I guess another way could be doing more thorough static analysis,
						//and seeing which array the string constants end up being written to.
						if(value instanceof String && ((String) value).endsWith(".jar")) {
							log.info("|-> Found Forge library: {}", value);
							sniffedLibraries.add((String) value);
						}
					}
				};
				
				else return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}
		
		try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getPath().toUri()), Collections.emptyMap())) {
			//read from magical hardcoded path inside the forge jar; this is where the auto-downloaded library paths are stored
			//TODO: applies from forge 1.3 through forge 1.5, dropped in 1.6
			//TODO: at least 1.5 includes additional "deobfuscation data" zip dep, but also contains a sys property to change download mirror
			Path coreFmlLibsPath = forgeFs.getPath("/cpw/mods/fml/relauncher/CoreFMLLibraries.class");
			if(Files.exists(coreFmlLibsPath)) {
				project.getLogger().info("|-> Parsing cpw.mods.fml.relauncher.CoreFMLLibraries...");
				try(InputStream in = Files.newInputStream(coreFmlLibsPath)) {
					new ClassReader(in).accept(new LibrarySniffingClassVisitor(null, sniffedLibraries), ClassReader.SKIP_FRAMES); //just don't need frames
				}
			} else {
				project.getLogger().info("|-> No cpw.mods.fml.relauncher.CoreFMLLibraries class in this jar.");
			}
		}
		
		return this;
	}
	
	public ForgeDependencyFetcher fetch() throws Exception {
		Path forgeLibsFolder = getCacheDir()
			.resolve("forgeLibs")
			.resolve(forge.getDepString().replaceAll("[^A-Za-z0-9.-]", "_"));
		
		cleanOnRefreshDependencies(forgeLibsFolder);
		
		Files.createDirectories(forgeLibsFolder);
		for(String lib : sniffedLibraries) {
			Path dest = forgeLibsFolder.resolve(lib);
			resolvedLibraries.add(dest);
			
			new DownloadSession(fmlLibrariesBaseUrl + lib, project)
				.dest(dest)
				.etag(true)
				.gzip(false)
				.skipIfExists()
				.download();
		}
		
		return this;
	}
	
	public ForgeDependencyFetcher installDependenciesToProject(String config, DependencyHandler deps, Function<Path, FileCollection> files) {
		for(Path resolvedLibrary : resolvedLibraries) {
			deps.add(config, files.apply(resolvedLibrary));
		}
		
		return this;
	}
}
