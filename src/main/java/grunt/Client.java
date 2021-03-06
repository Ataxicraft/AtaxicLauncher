package grunt;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.properties.PropertyMap;
import com.volmit.dumpster.M;

import grunt.ClientAuthentication.AuthenticationResponse;
import grunt.json.F;
import grunt.json.JSONArray;
import grunt.json.JSONObject;
import grunt.json.StreamGobbler;
import grunt.ui.ProgressGameCrash;
import grunt.ui.ProgressLogin;
import grunt.ui.ProgressRunning;
import grunt.ui.ProgressStart;
import grunt.ui.UX;
import grunt.util.DLQ;
import grunt.util.GList;
import grunt.util.GMap;
import grunt.util.JavaFinder;
import grunt.util.JavaInfo;
import grunt.util.OSF;
import grunt.util.OSF.OS;
import grunt.util.OldPropertyMapSerializer;
import squawk.Squawk;

public class Client
{
	private File fbase;
	private File fasm;
	private File fassets;
	private File fbin;
	private File fhome;
	private File fnatives;
	private File flibs;
	private File fobjects;
	private File fauth;
	private File fgame;
	private File fconf;
	public static JSONObject config;
	private ClientAuthentication auth;
	private ProgressLogin login;
	public static ProgressStart ps;
	public static ProgressRunning ru;
	public static double x = 0;
	public static double y = 0;
	private DLQ q;
	private static GMap<String, String> artifactRemapping;
	public static GList<String> lines = new GList<String>();
	private long lms = M.ms();

	public Client()
	{
		Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
		x = (int) ((dimension.getWidth() - 320) / 2);
		y = (int) ((dimension.getHeight() - 303) / 2);
		fbase = new File(new File(Grunt.localfs ? "." : System.getProperty("user.home")), "Ataxic");
		fasm = new File(fbase, "client");
		fauth = new File(fasm, "auth.ksg");
		fgame = new File(fbase, "game");
		fassets = new File(fgame, ".minecraft/assets");
		fobjects = new File(fassets, "objects");
		fbin = new File(fbase, "bin");
		fnatives = new File(fbin, "natives");
		flibs = new File(fbin, "libs");
		fconf = new File(fasm, "config.json");
		q = new DLQ(3);
		fobjects.mkdirs();
		fasm.mkdirs();
		fnatives.mkdirs();
		flibs.mkdirs();
		fgame.mkdirs();

		try
		{
			loadConfig();
		}

		catch(IOException e)
		{
			e.printStackTrace();
			fconf.delete();
			System.exit(1);
		}

		artifactRemapping = buildArtifactRemapping(new GMap<String, String>());
	}

	private void loadConfig() throws IOException
	{
		if(!fconf.exists())
		{
			fconf.getParentFile().mkdirs();
			JSONObject ja = new JSONObject();
			ja.put("memory-max", "6g");
			ja.put("throttle-launcher", true);
			PrintWriter pw = new PrintWriter(fconf);
			pw.println(ja.toString(4));
			pw.close();
		}

		BufferedReader bu = new BufferedReader(new FileReader(fconf));
		String c = "";
		String l = null;

		while((l = bu.readLine()) != null)
		{
			c += l;
		}

		bu.close();
		JSONObject ja = new JSONObject(c);
		config = ja;
	}

	private GMap<String, String> buildArtifactRemapping(GMap<String, String> map)
	{
		map.put("net.minecraftforge:forge:1.12.2-" + URLX.FORGE_VERSION + "", "https://files.minecraftforge.net/maven/net/minecraftforge/forge/1.12.2-" + URLX.FORGE_VERSION + "/forge-1.12.2-" + URLX.FORGE_VERSION + "-universal.jar");
		map.put("org.scala-lang:scala-xml_2.11:1.0.2", "http://central.maven.org/maven2/org/scala-lang/modules/scala-xml_2.11/1.0.2/scala-xml_2.11-1.0.2.jar");
		map.put("org.scala-lang:scala-swing_2.11:1.0.1", "http://central.maven.org/maven2/org/scala-lang/modules/scala-swing_2.11/1.0.1/scala-swing_2.11-1.0.1.jar");
		map.put("org.scala-lang:scala-parser-combinators_2.11:1.0.1", "http://central.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.11/1.0.1/scala-parser-combinators_2.11-1.0.1.jar");
		map.put("com.google.guava:guava:21.0", "https://libraries.minecraft.net/com/google/guava/guava/21.0/guava-21.0.jar");
		return map;
	}

	public void logIn()
	{
		try
		{
			auth = new ClientAuthentication(fauth);

			if(auth.authenticate().equals(AuthenticationResponse.SUCCESS))
			{
				onLoggedIn();
			}

			else
			{
				loginWithPassword();
			}
		}

		catch(ClassNotFoundException | IOException e)
		{
			e.printStackTrace();
			loginWithPassword();
		}
	}

	private void onLoggedIn()
	{
		System.out.println("Logged In as " + auth.getProfileName());

		try
		{
			downloadGame();

			if(!new File(fgame, "mods").exists() || new File(fgame, "mods").listFiles().length == 0)
			{
				System.out.println("Mods Folder " + new File(fgame, "mods").getAbsolutePath());

				System.out.println("Apply Patches...");
				System.out.println("Patching Game...");

				GList<Integer> m = new GList<>();

				for(int i = 0; i < Squawk.getLatestPatch(); i++)
				{
					m.add(i + 1);
				}

				Squawk.applyAllPatches(m);
			}

			int code = launchGame();
			System.out.println("Process exited with error code " + code);

			if(code != 0)
			{
				ProgressGameCrash c = new ProgressGameCrash();
				c.setVisible(true);
			}

			else
			{
				System.exit(0);
			}
		}

		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}
	}

	private int launchGame() throws IOException, InterruptedException
	{
		long memory = Platform.MEMORY.PHYSICAL.getTotalMemory();
		String target = "potato";

		if(memory > 8 * Math.pow(1024, 3))
		{
			target = "ultra";
		}

		System.out.println("Launching Game....");
		ru = new ProgressRunning();
		ru.setVisible(true);
		ProgressRunning.label.setText(target.equals("ultra") ? "Ultra" : "Potato" + " Mode");
		File main = new File(fgame, ".minecraft");
		GList<String> a = new GList<>();
		a.add(getDefaultJavaPath().replaceAll("jre", "jdk").replace("jre", "jdk"));
		a.add("-jar");
		a.add("Firefly.jar");
		a.add("vdi.js");
		a.add(target);
		ProcessBuilder b = new ProcessBuilder(a);
		b.directory(fgame);
		b.start().waitFor();
		Thread.sleep(3000);
		File libs = flibs;
		File natives = fnatives;
		String mainClassForge = "net.minecraft.launchwrapper.Launch";
		String properties = new GsonBuilder().registerTypeAdapter(PropertyMap.class, new OldPropertyMapSerializer()).create().toJson(auth.getProfileSettings());
		List<File> classpath = getLibClasspath(libs);
		GList<String> arguments = new GList<String>();
		File instanceConfig = new File(fgame, "instance.cfg");
		String jvmArgs = "";
		String maxMem = "";
		String minMem = "";
		String m = null;
		BufferedReader br = new BufferedReader(new FileReader(instanceConfig));

		while((m = br.readLine()) != null)
		{
			if(m.startsWith("JvmArgs="))
			{
				jvmArgs = m.substring(8).trim();
			}

			if(m.startsWith("MaxMemAlloc="))
			{
				maxMem = m.substring(12);
			}

			if(m.startsWith("MinMemAlloc="))
			{
				minMem = m.substring(12);
			}
		}

		br.close();
		main.mkdirs();
		arguments.add(getDefaultJavaPath().replaceAll("jre", "jdk").replace("jre", "jdk"));
		arguments.add("-Xmx" + maxMem + "M");
		arguments.add("-Xms" + minMem + "M");

		for(String i : jvmArgs.split(" "))
		{
			if(i.trim().isEmpty())
			{
				continue;
			}

			arguments.add(i);
		}

		arguments.add("-Djava.library.path=" + natives.getAbsolutePath());
		arguments.add("-Dorg.lwjgl.librarypath=" + natives.getAbsolutePath());
		arguments.add("-Dnet.java.games.input.librarypath=" + natives.getAbsolutePath());
		arguments.add("-Duser.home=" + main.getParentFile().getAbsolutePath());
		arguments.add("-Duser.language=en");

		if(OSF.getCurrentOS() == OS.WINDOWS)
		{
			arguments.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
		}

		arguments.add("-Djava.net.preferIPv4Stack=true");
		arguments.add("-Djava.net.useSystemProxies=true");
		arguments.add("-cp");

		StringBuilder cpb = new StringBuilder("");

		for(File f : classpath)
		{
			cpb.append(OSF.getJavaDelimiter());
			cpb.append(f.getAbsolutePath());
		}

		new File(fassets, "indexes").mkdirs();
		Files.copy(new File(fassets, "asset-index.json"), new File(fassets, "indexes/1.12.json"));

		cpb.deleteCharAt(0);
		arguments.add(cpb.toString());
		arguments.add("-Dlog4j.skipJansi=true");
		arguments.add(mainClassForge);
		arguments.add("--username");
		arguments.add(auth.getProfileName());
		arguments.add("--version");
		arguments.add("1.12.2");
		arguments.add("--gameDir");
		arguments.add(main.getAbsolutePath());
		arguments.add("--assetsDir");
		arguments.add(fassets.getAbsolutePath());
		arguments.add("--assetsIndex");
		arguments.add("1.12");
		arguments.add("--uuid");
		arguments.add(auth.getUuid());
		arguments.add("--accessToken");
		arguments.add(auth.getToken());
		arguments.add("--userProperties");
		arguments.add(properties);
		arguments.add("--userType");
		arguments.add(auth.getProfileType());
		arguments.add("--tweakClass");
		arguments.add("net.minecraftforge.fml.common.launcher.FMLTweaker");

		System.out.println("======================================================================");

		for(String i : arguments)
		{
			if(i.contains(";"))
			{
				for(String j : i.split(";"))
				{
					System.out.println(";;" + j + ";;");
				}
			}

			else
			{
				System.out.println(i);
			}
		}

		System.out.println("======================================================================");

		ProcessBuilder builder = new ProcessBuilder(arguments);

		builder.directory(main);
		System.out.println("==========================================================================");
		System.out.println("Min " + minMem + ", Max: " + maxMem + ", Args: " + jvmArgs);
		System.out.println("Launching Client!");

		try
		{
			Process process = builder.start();
			BufferedReader bu = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StreamGobbler se = new StreamGobbler(process.getErrorStream(), "CLIENT - ERROR");
			se.start();
			String line;
			int count = 0;
			int vec = 172;

			while((line = bu.readLine()) != null)
			{
				double pct = (((((((double) count / (double) vec) * 1.125) * 1.15) / 2D) * 1.42) * 1.33333333333) * 0.9;
				if(ru.isVisible() && (int) pct > 100 || line.contains("Mystcraft Start-Up Error Checking Completed"))
				{
					System.out.println("[GRUNT]: CLOSE UI");
					System.out.println("COUNT: " + count);
					Thread.sleep(10500);
					ru.setVisible(false);
					System.exit(0);
				}

				String km = "";

				if(line.contains(":"))
				{
					km = line.split(":")[line.split(":").length - 1];
				}

				else
				{
					km = line;
				}

				if(M.ms() - lms > 50)
				{
					ProgressRunning.lblLog.setText(km);
					ProgressRunning.panel.setProgress((int) ((int) (pct)));
					ProgressRunning.label.setText(F.pc((int) ((int) ((pct))) / 100D, 0));
					lms = M.ms();
				}

				count++;
			}

			int code = process.waitFor();
			se.interrupt();

			return code;
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return -1;
	}

	private List<File> getLibClasspath(File libs)
	{
		List<File> classpath = Lists.newArrayList();

		System.out.println("Building Classpath");

		for(File i : getFiles(libs))
		{
			classpath.add(new File(i.getPath()));
			System.out.println("Adding " + i.getName() + " to classpath");
		}

		classpath.add(new File(fbin, "client.jar"));

		return classpath;
	}

	private static List<File> getFiles(File folder)
	{
		List<File> files = new ArrayList<File>();

		for(File i : folder.listFiles())
		{
			if(i.isFile())
			{
				if(i.getName().equals("guava-15.0.jar"))
				{
					continue;
				}

				System.out.println("Reading Library: " + i.getAbsolutePath());
				files.add(i);
			}

			else
			{
				files.addAll(getFiles(i));
			}
		}

		return files;
	}

	private static String getDefaultJavaPath()
	{
		JavaInfo javaVersion;

		if(OSF.getCurrentOS() == OS.MACOSX)
		{
			javaVersion = JavaFinder.parseJavaVersion();

			if(javaVersion != null && javaVersion.path != null)
			{
				return javaVersion.path;
			}
		}

		else if(OSF.getCurrentOS() == OS.WINDOWS)
		{
			javaVersion = JavaFinder.parseJavaVersion();

			if(javaVersion != null && javaVersion.path != null)
			{
				return javaVersion.path.replace(".exe", "w.exe");
			}
		}

		return (System.getProperty("java.home") + "/bin/java").replaceAll("jre", "jdk");
	}

	private void downloadGame() throws IOException
	{
		File vm = new File(fasm, "version-manifest.json");
		File vmv = new File(fasm, "manifest.json");
		File iid = new File(fassets, "asset-index.json");
		File cli = new File(fbin, "client.jar");
		File pmda = new File(fasm, "patch.mda");
		File patchFolder = new File(fasm, "patches");
		UX.rgb = true;
		patchFolder.mkdirs();
		ps = new ProgressStart();
		q.q(URLX.VERSION_META, vm);
		int opn = -1337;

		if(pmda.exists())
		{
			BufferedReader bu = new BufferedReader(new FileReader(pmda));
			String line = bu.readLine();
			opn = Integer.valueOf(line.split(":")[1].trim());
			bu.close();
			pmda.delete();
		}

		q.q(URLX.PDS_META, pmda);
		q.q("https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-windows.jar", new File(flibs, "com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-windows.jar"));
		q.q("https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar", new File(flibs, "com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar"));
		q.flush();
		BufferedReader bu = new BufferedReader(new FileReader(pmda));
		String line = bu.readLine();
		bu.close();
		int patchNumber = Integer.valueOf(line.split(":")[1].trim());

		System.out.println("Current Patch Number: " + patchNumber);
		System.out.println("Old Patch Number: " + opn);

		if(opn > patchNumber && opn >= 0)
		{
			System.out.println("Old patches detected, rebasing...");
			if(patchFolder.exists())
			{
				for(File i : patchFolder.listFiles())
				{
					System.out.println("Deleted old patch " + i.getName());
					i.delete();
				}
			}

			if(fgame.exists())
			{
				delete(fgame);
			}

			if(fassets.exists())
			{
				delete(fassets);
			}
		}

		JSONObject jvm = readJSON(vm);
		writeJSON(jvm, vm);
		JSONArray ja = jvm.getJSONArray("versions");
		JSONObject m = null;

		for(int i = 0; i < ja.length(); i++)
		{
			JSONObject j = ja.getJSONObject(i);

			if(j.getString("id").equals("1.12.2") && j.getString("type").equals("release"))
			{
				m = j;
				break;
			}
		}

		q.q(m.getString("url"), vmv);
		q.flush();

		JSONObject manifest = readJSON(vmv);
		JSONObject assetIndex = manifest.getJSONObject("assetIndex");
		JSONObject downloads = manifest.getJSONObject("downloads");
		JSONObject client = downloads.getJSONObject("client");
		q.q(assetIndex.getString("url"), iid);
		q.flush();

		JSONObject assets = readJSON(iid);
		JSONArray libraries = manifest.getJSONArray("libraries");
		JSONObject objects = assets.getJSONObject("objects");
		Iterator<String> itObject = objects.keys();

		q.q(client.getString("url"), cli, client.getLong("size"));

		while(itObject.hasNext())
		{
			String key = itObject.next();
			JSONObject asset = objects.getJSONObject(key);
			long size = asset.getLong("size");
			String hash = asset.getString("hash");
			String hashRoot = hash.substring(0, 2);
			File fr = new File(fgame, ".minecraft/resourcepacks");
			File fx = new File(fr, "main");
			File f = new File(fx, key);
			File d = new File(fobjects, hashRoot);

			if(key.endsWith(".ogg"))
			{
				continue;
			}

			if(key.endsWith(".png"))
			{
				continue;
			}

			if(key.startsWith("minecraft"))
			{
				f = new File(fx, "assets/" + key);
			}

			f.getParentFile().mkdirs();
			File k = f;
			q.q("http://resources.download.minecraft.net/" + hashRoot + "/" + hash, f, size, new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						System.out.println("Copy asset: " + k + " > " + d);
						d.mkdirs();
						Files.copy(k, new File(d, hash));
					}

					catch(IOException e)
					{
						e.printStackTrace();
					}
				}
			});
		}

		for(int i = 0; i < libraries.length(); i++)
		{
			JSONObject lib = libraries.getJSONObject(i);
			JSONObject libDownloads = lib.getJSONObject("downloads");

			if(libDownloads.has("artifact"))
			{
				String name = lib.getString("name").replaceAll(":", "-") + ".jar";
				System.out.println(name);
				JSONObject artifact = libDownloads.getJSONObject("artifact");
				String path = artifact.getString("path");
				File f = new File(flibs, path);
				f.getParentFile().mkdirs();
				q.q(artifact.getString("url"), f, artifact.getLong("size"));
			}

			else if(lib.has("natives"))
			{
				JSONObject nativeset = lib.getJSONObject("natives");
				String raw = OSF.rawOS();

				if(nativeset.has(raw))
				{
					String classifier = nativeset.getString(raw).replace("${arch}", OSF.is64BitWindows() ? "64" : "32");
					JSONObject classifiers = libDownloads.getJSONObject("classifiers");

					if(classifiers.has(classifier))
					{
						JSONObject cl = classifiers.getJSONObject(classifier);
						String path = cl.getString("path");
						File f = new File(flibs, path);
						q.q(cl.getString("url"), f, cl.getLong("size"), new Runnable()
						{
							@Override
							public void run()
							{
								try
								{
									extractNatives(f, fnatives);
								}
								catch(IOException e)
								{
									e.printStackTrace();
								}
							}
						});
					}

					else
					{
						System.out.println("No classifier for " + classifier);
					}
				}

				else
				{
					System.out.println("WARNING No classifier RAW for " + raw);
				}
			}
		}

		JSONObject forge = new JSONObject(URLX.IFORGE);

		if(forge.has("libraries"))
		{
			JSONArray ilibs = forge.getJSONArray("libraries");

			for(int i = 0; i < ilibs.length(); i++)
			{
				JSONObject ilib = ilibs.getJSONObject(i);

				if(ilib.has("url"))
				{
					String name = ilib.getString("name");
					String curl = "http://central.maven.org/maven2/";

					Artifact a = new Artifact(name, curl);
					String kurl = a.getFormalUrl();

					if(artifactRemapping.containsKey(name))
					{
						System.out.println("Remapping artifact coordinates: " + name + " to \n" + artifactRemapping.get(name));
						kurl = artifactRemapping.get(name);
					}

					else
					{
						System.out.println("Mapping artifact coordinates: " + name + " to \n" + kurl);
					}

					a.getPath(flibs).getParentFile().mkdirs();
					q.q(kurl, a.getPath(flibs));
				}

				else
				{
					String name = ilib.getString("name");
					String curl = "https://libraries.minecraft.net/";
					Artifact a = new Artifact(name, curl);
					String kurl = a.getFormalUrl();

					if(artifactRemapping.containsKey(name))
					{
						System.out.println("Remapping artifact coordinates: " + name + " to \n" + artifactRemapping.get(name));
						kurl = artifactRemapping.get(name);
					}

					else
					{
						System.out.println("Mapping artifact coordinates: " + name + " to \n" + kurl);
					}

					a.getPath(flibs).getParentFile().mkdirs();
					q.q(kurl, a.getPath(flibs));
				}
			}
		}

		for(String i : artifactRemapping.k())
		{
			String path = i.split(":")[0].replaceAll("\\.", "/");
			String version = i.split(":")[2];
			String artifact = i.split(":")[1];
			String fpath = path + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
			File nf = new File(flibs, fpath);
			nf.getParentFile().mkdirs();
			q.q(artifactRemapping.get(i), nf);
		}

		Squawk.setup(patchFolder, fgame);

		int cpa = patchNumber;
		boolean missing = false;
		List<Integer> missed = new ArrayList<Integer>();

		for(int i = 1; i <= cpa; i++)
		{
			File patchFile = new File(patchFolder, "P." + i);

			if(!patchFile.exists())
			{
				System.out.println("Missing Patch #" + i);
				q.q(URLX.PDS + i, patchFile);
				missing = true;
				missed.add(i);
			}

			else
			{
				System.out.println("Reading Patch #" + i);
			}
		}

		q.flush();

		if(missing)
		{
			System.out.println("Patching Game...");
			Squawk.applyAllPatches(missed);
		}

		try
		{
			extractNatives(new File(flibs, "com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-windows.jar"), fnatives);
			extractNatives(new File(flibs, "com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar"), fnatives);
		}

		catch(Throwable e)
		{

		}

		cleanup();
		System.out.println("Game Downloaded");
		ps.setVisible(false);
		x = ps.getLocation().getX();
		y = ps.getLocation().getY();
	}

	private void cleanup()
	{
		try
		{
			for(File i : fnatives.listFiles())
			{
				if(i.getName().equals("META-INF") && i.isDirectory())
				{
					for(File j : i.listFiles())
					{
						j.delete();
					}

					i.delete();
				}

				else if(i.getName().equals("META-INF") && i.isFile())
				{
					i.delete();
				}
			}
		}

		catch(Exception e)
		{

		}
	}

	private void loginWithPassword()
	{
		boolean[] fk = new boolean[] {false};

		login = new ProgressLogin()
		{
			private static final long serialVersionUID = -5980962525568940052L;

			@Override
			public boolean onSubmit(String username, String password)
			{
				try
				{
					auth = new ClientAuthentication(fauth, username, password);
				}

				catch(Exception e)
				{
					e.printStackTrace();
				}

				AuthenticationResponse r = auth.authenticate();

				if(r.equals(AuthenticationResponse.SUCCESS))
				{
					fk[0] = true;
					login.setVisible(false);
					Client.x = login.getLocation().getX();
					Client.y = login.getLocation().getY();
				}

				return r.equals(AuthenticationResponse.SUCCESS);
			}
		};

		login.setVisible(true);

		while(!fk[0])
		{
			try
			{
				Thread.sleep(100);
			}

			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		onLoggedIn();
	}

	private static void writeJSON(JSONObject o, File f) throws IOException
	{
		FileWriter fw = new FileWriter(f);
		PrintWriter pw = new PrintWriter(fw);

		pw.println(o.toString());

		pw.close();
	}

	private static JSONObject readJSON(File man) throws IOException
	{
		FileReader fr = new FileReader(man);
		BufferedReader bu = new BufferedReader(fr);

		String line;
		String content = "";

		while((line = bu.readLine()) != null)
		{
			content += line;
		}

		bu.close();

		return new JSONObject(content);
	}

	private static void extractNatives(File zip, File lfolder) throws IOException
	{
		final ZipFile file = new ZipFile(zip);

		try
		{
			final Enumeration<? extends ZipEntry> entries = file.entries();

			while(entries.hasMoreElements())
			{
				final ZipEntry entry = entries.nextElement();

				if(entry.getName().contains("META") || entry.getName().contains("MANIFEST"))
				{
					continue;
				}

				writeStream(entry.getName(), file.getInputStream(entry), lfolder, zip);
			}
		}

		finally
		{
			file.close();
		}
	}

	public static void extractAll(File zip, File lfolder) throws IOException
	{
		final ZipFile file = new ZipFile(zip);

		try
		{
			final Enumeration<? extends ZipEntry> entries = file.entries();

			while(entries.hasMoreElements())
			{
				final ZipEntry entry = entries.nextElement();

				if(entry.isDirectory())
				{
					new File(lfolder, entry.getName()).mkdirs();
					continue;
				}

				writeStream(entry.getName(), file.getInputStream(entry), lfolder, zip);
			}
		}

		finally
		{
			file.close();
		}
	}

	private static int writeStream(String n, final InputStream is, File lfolder, File zip) throws IOException
	{
		lfolder.mkdirs();
		FileOutputStream fos = new FileOutputStream(new File(lfolder, n));
		final byte[] buf = new byte[8192];
		int read = 0;
		int cntRead;

		while((cntRead = is.read(buf, 0, buf.length)) >= 0)
		{
			read += cntRead;
			fos.write(buf, 0, cntRead);
		}

		System.out.println("Extract: " + n + " from " + zip.getName());

		fos.close();

		return read;
	}

	public File getFbase()
	{
		return fbase;
	}

	public File getFasm()
	{
		return fasm;
	}

	public File getFassets()
	{
		return fassets;
	}

	public File getFbin()
	{
		return fbin;
	}

	public File getFhome()
	{
		return fhome;
	}

	public File getFnatives()
	{
		return fnatives;
	}

	public File getFlibs()
	{
		return flibs;
	}

	public File getFobjects()
	{
		return fobjects;
	}

	public File getFauth()
	{
		return fauth;
	}

	public ClientAuthentication getAuth()
	{
		return auth;
	}

	public DLQ getQ()
	{
		return q;
	}

	private void delete(File f)
	{
		if(f.exists() && f.isDirectory())
		{
			for(File i : f.listFiles())
			{
				delete(i);
			}
		}

		System.out.println("Deleting " + f.getPath());
		f.delete();
	}
}
