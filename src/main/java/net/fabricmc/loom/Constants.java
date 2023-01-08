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

package net.fabricmc.loom;

public class Constants {
	//Task groups
	public static final String TASK_GROUP_CLEANING = "fabric.clean";
	public static final String TASK_GROUP_IDE = "fabric.ide";
	public static final String TASK_GROUP_PLUMBING = "fabric.plumbing";
	public static final String TASK_GROUP_RUNNING = "fabric.run";
	public static final String TASK_GROUP_TOOLS = "fabric.tools";
	
	//Lol why is this here
	public static final String SYSTEM_ARCH = System.getProperty("os.arch").equals("64") ? "64" : "32";

	//Configuration names
	public static final String EVERY_UNMAPPED_MOD = "everyUnmappedMod";
	
	public static final String MINECRAFT = "minecraft";
	public static final String MINECRAFT_DEPENDENCIES = "minecraftLibraries";
	public static final String MINECRAFT_NAMED = "minecraftNamed";
	
	public static final String MAPPINGS = "mappings";
	public static final String MAPPINGS_FINAL = "mappingsFinal";
	
	public static final String FORGE = "forge";
	public static final String FORGE_DEPENDENCIES = "forgeLibraries";

	//public static final String DEV_LAUNCH_INJECTOR_VERSION = "0.2.0+build.6";
}
