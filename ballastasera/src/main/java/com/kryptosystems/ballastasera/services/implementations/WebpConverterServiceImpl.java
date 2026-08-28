package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class WebpConverterServiceImpl implements WebpConverterService {

    private static final int QUALITY = 80;
    private static final int COMPRESSION_EFFORT = 6;
    private static final int MAX_LONG_EDGE = 1080; // resolución nativa de Instagram
    private static final int TIMEOUT_SECONDS = 30;

    /** Corre cwebp como subproceso aislado: un crash nativo mata solo el
     * subproceso, nunca la JVM de Spring Boot. */
    @Override
    public byte[] convertToWebp(byte[] content) {
        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = Files.createTempFile("flyer-raw-", ".tmp");
            outputFile = Files.createTempFile("flyer-final-", ".webp");
            Files.write(inputFile, content);

            List<String> command = new ArrayList<>(List.of(
                    "cwebp", "-q", String.valueOf(QUALITY), "-m", String.valueOf(COMPRESSION_EFFORT)));
            addResizeIfNeeded(command, content);
            command.add(inputFile.toString());
            command.add("-o");
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new MediaStorageException("cwebp timed out after " + TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new MediaStorageException("cwebp exited with code " + process.exitValue());
            }
            return Files.readAllBytes(outputFile);

        } catch (IOException e) {
            throw new MediaStorageException("Failed to convert flyer to webp", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaStorageException("Webp conversion interrupted", e);
        } finally {
            deleteQuietly(inputFile);
            deleteQuietly(outputFile);
        }
    }

    /** Solo reduce si el lado largo supera MAX_LONG_EDGE; nunca hace upscale
     * de imagenes mas chicas. Deteccion de dimensiones via ImageIO: funciona
     * para jpg/png; un input .webp no tiene reader registrado sin plugins
     * extra, asi que en ese caso se omite el cap de tamaño y solo se
     * recomprime a la calidad configurada. */
    private void addResizeIfNeeded(List<String> command, byte[] content) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
        if (image == null) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (Math.max(width, height) <= MAX_LONG_EDGE) {
            return;
        }
        command.add("-resize");
        if (width >= height) {
            command.add(String.valueOf(MAX_LONG_EDGE));
            command.add("0");
        } else {
            command.add("0");
            command.add(String.valueOf(MAX_LONG_EDGE));
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
