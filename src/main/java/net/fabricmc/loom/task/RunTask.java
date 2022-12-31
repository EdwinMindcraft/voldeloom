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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.RunConfig;
import org.gradle.api.tasks.JavaExec;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class RunTask extends JavaExec implements LoomTaskExt {
	@Inject
	public RunTask(RunConfig config) throws Exception {
		setGroup("minecraftMapped");
		
		LoomGradleExtension extension = getLoomGradleExtension();
		config = config.cook(extension);

		//Classpath
		List<String> classpath = new ArrayList<>();

		//TODO this should also go in RunConfig#cook
		for(File file : getProject().getConfigurations().getByName("runtimeClasspath").getFiles()) {
			//todo Pretty big hack to skip putting coremods on the classpath when they're already in the coremods folder
			// Need to find a better way to do this tbh
			Path coremodsDir = config.resolveRunDir().resolve("coremods");
			if(!Files.exists(coremodsDir.resolve(file.getName()))) {
				classpath.add(file.getAbsolutePath());
			}
		}
		for (Path file : extension.getUnmappedMods()) {
			if (Files.isRegularFile(file)) {
				classpath.add(file.toFile().getAbsolutePath());
			}
		}
		classpath(classpath);
		
		//Arguments
//		List<String> argsSplit = new ArrayList<>();
//		String[] args = config.stringifyProgramArgs().split(" ");
//		int partPos = -1;
//
//		for (int i = 0; i < args.length; i++) {
//			if (partPos < 0) {
//				if (args[i].startsWith("\"")) {
//					if (args[i].endsWith("\"")) {
//						argsSplit.add(args[i].substring(1, args[i].length() - 1));
//					} else {
//						partPos = i;
//					}
//				} else {
//					argsSplit.add(args[i]);
//				}
//			} else if (args[i].endsWith("\"")) {
//				StringBuilder builder = new StringBuilder(args[partPos].substring(1));
//
//				for (int j = partPos + 1; j < i; j++) {
//					builder.append(" ").append(args[j]);
//				}
//
//				builder.append(" ").append(args[i], 0, args[i].length() - 1);
//				argsSplit.add(builder.toString());
//				partPos = -1;
//			}
//		}

		jvmArgs(config.getVmArgs());
		args(config.getProgramArgs());

		//Main class
		getMainClass().set(config.getMainClass()); //todo gradle 4? this is relatively new
		//setMain(config.mainClass);

		//Pwd
		Path runDir = getProject().getRootDir().toPath().resolve(config.getRunDir());
		Files.createDirectories(runDir);
		setWorkingDir(runDir);
		
		//Stdin
		setStandardInput(System.in);
	}
}
