/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.lorenztiny.LorenzTiny;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class MigrateMappingsTask extends DefaultTask {
	private Path inputDir;
	private Path outputDir;
	private String mappings;

	public MigrateMappingsTask() {
		setGroup("fabric");
		getOutputs().upToDateWhen(t -> false);
		inputDir = getProject().file("src/main/java").toPath();
		outputDir = getProject().file("remappedSrc").toPath();
	}

	@Option(option = "input", description = "Java source file directory")
	public void setInputDir(String inputDir) {
		this.inputDir = getProject().file(inputDir).toPath();
	}

	@Option(option = "output", description = "Remapped source output directory")
	public void setOutputDir(String outputDir) {
		this.outputDir = getProject().file(outputDir).toPath();
	}

	@Option(option = "mappings", description = "Target mappings")
	public void setMappings(String mappings) {
		this.mappings = mappings;
	}

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		project.getLogger().lifecycle(":loading mappings");

		if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
			throw new IllegalArgumentException("Could not find input directory: " + inputDir.toAbsolutePath());
		}

		Files.createDirectories(outputDir);

		File mappings = loadMappings();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		try {
			TinyTree currentMappings = mappingsProvider.getMappings();
			TinyTree targetMappings = getMappings(mappings);
			migrateMappings(project, extension.getMinecraftMappedProvider(), inputDir, outputDir, currentMappings, targetMappings);
			project.getLogger().lifecycle(":remapped project written to " + outputDir.toAbsolutePath());
		} catch (IOException e) {
			throw new IllegalArgumentException("Error while loading mappings", e);
		}
	}

	private File loadMappings() {
		Project project = getProject();

		if (mappings == null || mappings.isEmpty()) {
			throw new IllegalArgumentException("No mappings were specified. Use --mappings=\"\" to specify target mappings");
		}

		Set<File> files;

		try {
			files = project.getConfigurations().detachedConfiguration(project.getDependencies().create(mappings)).resolve();
		} catch (IllegalDependencyNotation ignored) {
			project.getLogger().info("Could not locate mappings, presuming V2 Yarn");

			try {
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().module(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings, "classifier", "v2"))).resolve();
			} catch (GradleException ignored2) {
				project.getLogger().info("Could not locate mappings, presuming V1 Yarn");
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().module(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings))).resolve();
			}
		}

		if (files.isEmpty()) {
			throw new IllegalArgumentException("Mappings could not be found");
		}

		return Iterables.getOnlyElement(files);
	}

	@SuppressWarnings("RedundantCast") //newFileSystem type ascription is needed for Java forwards-compatibility reasons
	private static TinyTree getMappings(File mappings) throws IOException {
		Path temp = Files.createTempFile("mappings", ".tiny");

		try (FileSystem fileSystem = FileSystems.newFileSystem(mappings.toPath(), (ClassLoader) null)) {
			Files.copy(fileSystem.getPath("mappings/mappings.tiny"), temp, StandardCopyOption.REPLACE_EXISTING);
		}

		try (BufferedReader reader = Files.newBufferedReader(temp)) {
			return TinyMappingFactory.loadWithDetection(reader);
		}
	}

	private static void migrateMappings(Project project, MinecraftMappedProvider minecraftMappedProvider,
										Path inputDir, Path outputDir, TinyTree currentMappings, TinyTree targetMappings
	) throws IOException {
		project.getLogger().lifecycle(":joining mappings");
		MappingSet mappingSet = LorenzTiny.readMappings(currentMappings, targetMappings,
						"intermediary", "named").read();

		project.getLogger().lifecycle(":remapping");
		Mercury mercury = SourceRemapper.createMercuryWithClassPath(project, false);

		mercury.getClassPath().add(minecraftMappedProvider.getMappedJar().toPath());
		mercury.getClassPath().add(minecraftMappedProvider.getIntermediaryJar().toPath());

		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		try {
			mercury.rewrite(inputDir, outputDir);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap fully!", e);
		}

		project.getLogger().lifecycle(":cleaning file descriptors");
		System.gc();
	}
}
