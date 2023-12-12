package com.gpuExtended.util;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.shader.ShaderException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher implements Runnable
{
    public enum ReloadType {
        Full,
        HotReload
    }

    private ArrayList<Path> srcPaths = new ArrayList<>(); // src / main / resources /
    private ArrayList<Path> buildPaths = new ArrayList<>(); // build / resources / main /

    private final Map<Path, Long> lastModifiedTimes = new HashMap<>();
    private final long debounceMillis = 500;

    private ArrayList<String> pathsToWatch = new ArrayList<>();

    public FileWatcher()
    {
        pathsToWatch.add("shaders/glsl");
        pathsToWatch.add("environment");

        for (String path : pathsToWatch)
        {
            URL url = FileWatcher.class.getClassLoader().getResource(path);
            File pathDir = null;
            try
            {
                pathDir = new File(url.toURI());
            }
            catch (URISyntaxException e)
            {
                pathDir = new File(url.getPath());
            }

            buildPaths.add(Paths.get(pathDir.getAbsolutePath()));
            srcPaths.add(Paths.get(pathDir.getAbsolutePath().replace("build\\resources\\main", "src\\main\\resources")));
        }
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService())
        {
            for(int i = 0; i < srcPaths.size(); i++)
            {
                Path srcPath = srcPaths.get(i);
                Path buildPath = buildPaths.get(i);

                srcPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); // restore interrupted status
                        break;
                    }

                    Thread.sleep(debounceMillis);

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if(event.context().toString().endsWith("~"))
                            continue;

                        if (event.kind() == OVERFLOW)
                            continue;

                        Path sourceFile = Path.of(srcPath + "\\" + event.context());
                        Path destFile = Path.of(buildPath + "\\" + event.context());

                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        {
                            long currentTime = System.currentTimeMillis();
                            Long lastModifiedTime = lastModifiedTimes.get(sourceFile);

                            if (lastModifiedTime == null || (currentTime - lastModifiedTime) > debounceMillis) {
                                lastModifiedTimes.put(sourceFile, currentTime);
                                String fileName = event.context().toString();

                                System.out.println("File Changed: " + fileName);
                                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);

                                GpuExtendedPlugin.Instance.Reload(ReloadType.Full);
                                break;
                            }
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ShaderException e) {
            throw new RuntimeException(e);
        }
    }
}
