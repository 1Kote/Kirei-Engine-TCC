package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy para detectar arquivos duplicados por hash
 */
public class DuplicateDetectionStrategy implements ScheduledTaskStrategy
{
    private static final Logger logger = LogManager.getLogger(DuplicateDetectionStrategy.class);
    private final Map<String, List<Path>> duplicateMap = new HashMap<>();

    @Override
    public void execute(Configuration config)
    {
        logger.info("[DUPLICATE DETECTION] Iniciando detecção de duplicados...");
        duplicateMap.clear();

        //Percorre todas as pastas monitoradas
        for (String folderPath : config.getMonitorFolders())
        {
            scanDirectoryForDuplicates(Paths.get(folderPath));
        }

        //Processa duplicados encontrados
        processDuplicates();
    }

    /**
     * Escaneia diretório recursivamente
     */
    private void scanDirectoryForDuplicates(Path directory)
    {
        try
        {
            Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(this::isRelevantFile) // Filtra arquivos relevantes
                    .forEach(this::addToHash);
        }
        catch (IOException e)
        {
            logger.error("Erro escaneando diretório: {}", directory, e);
        }
    }

    /**
     * Verifica se arquivo é relevante para detecção
     */
    private boolean isRelevantFile(Path path)
    {
        try
        {
            long size = Files.size(path);
            //Ignora arquivos muito pequenos (< 1KB) ou muito grandes (> 1GB)
            return size > 1024 && size < 1_073_741_824L;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * Calcula hash do arquivo e adiciona ao mapa
     */
    private void addToHash(Path file)
    {
        try
        {
            String hash = calculateFileHash(file);
            duplicateMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
        }
        catch (Exception e)
        {
            logger.debug("Erro calculando hash: {}", file, e);
        }
    }

    /**
     * Calcula hash SHA-256 do arquivo
     */
    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder sb = new StringBuilder();

        //Converte bytes para hex
        for (byte b : hashBytes)
        {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * Processa e reporta duplicados encontrados
     */
    private void processDuplicates()
    {
        int duplicateGroups = 0;
        long wastedSpace = 0;

        //Filtra apenas grupos com duplicados reais
        for (Map.Entry<String, List<Path>> entry : duplicateMap.entrySet())
        {
            List<Path> files = entry.getValue();

            //Se há mais de um arquivo com mesmo hash
            if (files.size() > 1)
            {
                duplicateGroups++;

                try
                {
                    long fileSize = Files.size(files.get(0));
                    wastedSpace += fileSize * (files.size() - 1); // -1 porque um deve ser mantido

                    logger.warn("DUPLICADOS ENCONTRADOS ({}): {}", files.size(),
                            files.stream().map(Path::toString).toList());
                }
                catch (IOException e)
                {
                    logger.debug("Erro obtendo tamanho do arquivo", e);
                }
            }
        }

        logger.info("[DUPLICATE DETECTION] Grupos duplicados: {}, Espaço desperdiçado: {} MB",
                duplicateGroups, wastedSpace / (1024 * 1024));
    }
}