package com.gpuExtended.util;

import com.gpuExtended.GpuExtendedPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class FileWatcher implements Runnable
{
    private Path srcPath; // src / main / resources / shaders
    private Path buildPath; // build / resources / main / shaders

    private final Map<Path, Long> lastModifiedTimes = new HashMap<>();
    private final long debounceMillis = 500;

    public FileWatcher(String path) {
        URL url = FileWatcher.class.getClassLoader().getResource("shaders/glsl/");
        File shaderDir = null;
        try
        {
            shaderDir = new File(url.toURI());
        }
        catch (URISyntaxException e)
        {
            shaderDir = new File(url.getPath());
        }

        buildPath = Paths.get(shaderDir.getAbsolutePath());
        srcPath = Paths.get(shaderDir.getAbsolutePath().replace("build\\resources\\main", "src\\main\\resources"));

        System.out.println("Src Path: " + srcPath);
        System.out.println("Build Path: " + buildPath);
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            srcPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            HashSet<String> changedFiles = new HashSet<String>();
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

                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                    {
                        Path source = Path.of(srcPath + "\\" + event.context());
                        Path destination = Path.of(buildPath + "\\" + event.context());

                        long currentTime = System.currentTimeMillis();
                        Long lastModifiedTime = lastModifiedTimes.get(source);

                        if (lastModifiedTime == null || (currentTime - lastModifiedTime) > debounceMillis) {
                            lastModifiedTimes.put(source, currentTime);
                            System.out.println("File Changed: " + event.context());

                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                            GpuExtendedPlugin.Instance.Restart();
                            break;
                            // Recompile shaders
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
