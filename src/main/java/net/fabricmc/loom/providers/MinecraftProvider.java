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

package net.fabricmc.loom.providers;

import com.google.gson.Gson;
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.WellKnownLocations;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MinecraftProvider extends DependencyProvider {
	public MinecraftProvider(Project project, LoomGradleExtension extension, ForgeProvider forge) {
		super(project, extension);
		this.forge = forge;
	}
	
	private final ForgeProvider forge;
	
	private String minecraftVersion;
	private String minecraftJarStuff;
	private MinecraftVersionInfo versionInfo;
	private Path minecraftJson;
	private Path minecraftClientJar;
	private Path minecraftServerJar;

	@Override
	public void decorateProject() throws Exception {
		//deps
		DependencyInfo minecraftDependency = getSingleDependency(Constants.MINECRAFT);
		minecraftVersion = minecraftDependency.getDependency().getVersion();
		
		//TODO remove this dep, move "jar stuff" to ForgePatchedProvider or remove it 
		minecraftJarStuff = minecraftDependency.getDependency().getVersion() + "-forge-" + forge.getVersion();
		
		//outputs (+versionInfo)
		Path userCache = WellKnownLocations.getUserCache(project);
		minecraftJson = userCache.resolve("minecraft-" + minecraftVersion + "-info.json");
		minecraftClientJar = userCache.resolve("minecraft-" + minecraftVersion + "-client.jar");
		minecraftServerJar = userCache.resolve("minecraft-" + minecraftVersion + "-server.jar");
		
		//execution
		boolean offline = project.getGradle().getStartParameter().isOffline();
		downloadMcJson(offline);

		try(BufferedReader reader = Files.newBufferedReader(minecraftJson)) {
			versionInfo = new Gson().fromJson(reader, MinecraftVersionInfo.class);
		}

		if(offline) {
			if(Files.exists(minecraftClientJar) && Files.exists(minecraftServerJar)) {
				project.getLogger().debug("Found client and server jars, presuming up-to-date");
			} else {
				throw new GradleException("Missing jar(s); Client: " + Files.exists(minecraftClientJar) + ", Server: " + Files.exists(minecraftServerJar));
			}
		} else {
			downloadJars(project.getLogger());
		}
	}
	
	private void downloadMcJson(boolean offline) throws IOException {
		Path manifests = WellKnownLocations.getUserCache(project).resolve("version_manifest.json");

		if (offline) {
			if (Files.exists(manifests)) {
				//If there is the manifests already we'll presume that's good enough
				project.getLogger().debug("Found version manifests, presuming up-to-date");
			} else {
				//If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Version manifests not found at " + manifests.toAbsolutePath());
			}
		} else {
			project.getLogger().debug("Downloading version manifests");
			new DownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json", project.getLogger())
				.dest(manifests)
				.etag(true)
				.gzip(true)
				.download();
		}

		ManifestVersion mcManifest;
		try(BufferedReader reader = Files.newBufferedReader(manifests)) {
			mcManifest = new Gson().fromJson(reader, ManifestVersion.class);
		}

		Optional<ManifestVersion.Versions> optionalVersion = Optional.empty();
		
		if (extension.customManifest != null) {
			ManifestVersion.Versions customVersion = new ManifestVersion.Versions();
			customVersion.id = minecraftVersion;
			customVersion.url = extension.customManifest;
			optionalVersion = Optional.of(customVersion);
			project.getLogger().lifecycle("Using custom minecraft manifest");
		}

		if (!optionalVersion.isPresent()) {
			optionalVersion = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst();
		}

		if (optionalVersion.isPresent()) {
			if (offline) {
				if (Files.exists(minecraftJson)) {
					//If there is the manifest already we'll presume that's good enough
					project.getLogger().debug("Found Minecraft {} manifest, presuming up-to-date", minecraftVersion);
				} else {
					//If we don't have the manifests then there's nothing more we can do
					throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at " + minecraftJson.toAbsolutePath());
				}
			} else {
				if (Files.notExists(minecraftJson)) {
					project.getLogger().debug("Downloading Minecraft {} manifest", minecraftVersion);
					new DownloadSession(optionalVersion.get().url, project.getLogger())
						.dest(minecraftJson)
						.gzip(true)
						.etag(true)
						.download();
				}
			}
		} else {
			throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
		}
	}

	private void downloadJars(Logger logger) throws IOException {
		if (Files.notExists(minecraftClientJar) || (!Checksum.compareSha1(minecraftClientJar, versionInfo.downloads.get("client").sha1))) {
			logger.debug("Downloading Minecraft {} client jar", minecraftVersion);
			new DownloadSession(versionInfo.downloads.get("client").url, logger)
				.dest(minecraftClientJar)
				.etag(true)
				.gzip(false) //TODO why do i get nonmatching hashes using this downloader + gzip?
				.download();
		}
		
		if (Files.notExists(minecraftServerJar) || (!Checksum.compareSha1(minecraftServerJar, versionInfo.downloads.get("server").sha1))) {
			logger.debug("Downloading Minecraft {} server jar", minecraftVersion);
			new DownloadSession(versionInfo.downloads.get("server").url, logger)
				.dest(minecraftServerJar)
				.etag(true)
				.gzip(false) //TODO why do i get nonmatching hashes using this downloader + gzip?
				.download();
		}
	}
	
	public Path getClientJar() {
		return minecraftClientJar;
	}
	
	public Path getServerJar() {
		return minecraftServerJar;
	}

	public String getVersion() {
		return minecraftVersion;
	}
	
	public MinecraftVersionInfo getVersionManifest() {
		return versionInfo;
	}
	
	public String getJarStuff() {
		return minecraftJarStuff;
	}
	
	public static class ManifestVersion {
		public List<Versions> versions = new ArrayList<>();
	
		public static class Versions {
			public String id, url;
		}
	}
}
