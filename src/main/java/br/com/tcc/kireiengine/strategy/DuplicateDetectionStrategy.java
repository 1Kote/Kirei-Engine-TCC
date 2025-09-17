package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.DuplicateDetectionConfig;
import br.com.tcc.kireiengine.config.model.DuplicateRulesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Strategy para detectar arquivos duplicados por hash
 */
public class DuplicateDetectionStrategy implements ScheduledTaskStrategy
{
    private static final Logger logger = LogManager.getLogger(DuplicateDetectionStrategy.class);
    private final Map<String, List<FileInfo>> duplicateMap = new HashMap<>();
    private final AtomicLong totalScannedFiles = new AtomicLong(0);
    private final AtomicLong totalScannedSize = new AtomicLong(0);

    @Override
    public void execute(Configuration config)
    {
        DuplicateDetectionConfig duplicateConfig = config.getDuplicateDetectionConfig();

        //Validação se detecção de duplicados está habilitada
        if (duplicateConfig == null || !duplicateConfig.isEnabled())
        {
            logger.debug("DUPLICATE DETECTION: Detecção de duplicados desabilitada.");
            return;
        }

        logger.info("[DUPLICATE DETECTION] Iniciando varredura de duplicados...");

        //Reset dos contadores
        duplicateMap.clear();
        totalScannedFiles.set(0);
        totalScannedSize.set(0);

        long startTime = System.currentTimeMillis();

        //Escaneamento de todas as pastas monitoradas
        for (String folderPath : config.getMonitorFolders())
        {
            scanDirectoryForDuplicates(Paths.get(folderPath), duplicateConfig.getRules());
        }

        long scanTime = System.currentTimeMillis() - startTime;

        //Processamento dos duplicados encontrados
        processDuplicates(duplicateConfig.getRules(), scanTime);
    }

    /**
     * Escaneia diretório recursivamente procurando duplicados
     */
    private void scanDirectoryForDuplicates(Path directory, DuplicateRulesConfig rules)
    {
        try
        {
            Files.walkFileTree(directory, new FileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                {
                    //Validação se arquivo é relevante para análise
                    if (isRelevantFile(file, attrs, rules))
                    {
                        processFile(file, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                {
                    logger.debug("DUPLICATE DETECTION: Falha ao acessar: {}", file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
            logger.error("DUPLICATE DETECTION: Erro escaneando diretório: {}", directory, e);
        }
    }

    /**
     * Valida se arquivo deve ser processado
     */
    private boolean isRelevantFile(Path file, BasicFileAttributes attrs, DuplicateRulesConfig rules)
    {
        //Ignora links simbólicos e diretórios
        if (!attrs.isRegularFile())
        {
            return false;
        }

        long size = attrs.size();

        //Filtra por tamanho se configurado
        if (rules != null)
        {
            if (rules.getMinFileSizeBytes() > 0 && size < rules.getMinFileSizeBytes())
            {
                return false;
            }
            if (rules.getMaxFileSizeBytes() > 0 && size > rules.getMaxFileSizeBytes())
            {
                return false;
            }
        }

        //Ignora arquivos de sistema comuns
        String fileName = file.getFileName().toString().toLowerCase();
        return !fileName.equals(".ds_store") &&
                !fileName.equals("thumbs.db") &&
                !fileName.startsWith(".");
    }

    /**
     * Processa arquivo individual calculando hash
     */
    private void processFile(Path file, BasicFileAttributes attrs)
    {
        try
        {
            String hash = calculateFileHash(file);
            FileInfo fileInfo = new FileInfo(file, attrs.size(), attrs.lastModifiedTime().toMillis());

            duplicateMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(fileInfo);

            totalScannedFiles.incrementAndGet();
            totalScannedSize.addAndGet(attrs.size());

        }
        catch (Exception e)
        {
            logger.debug("DUPLICATE DETECTION: Erro processando {}: {}", file, e.getMessage());
        }
    }

    /**
     * Calcula hash SHA-256 otimizado para arquivos grandes
     */
    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        //Para arquivos pequenos, lê tudo
        if (Files.size(file) <= 8192) // 8KB
        {
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(fileBytes);
            return bytesToHex(hashBytes);
        }

        //Para arquivos grandes, lê em blocos
        try (var inputStream = Files.newInputStream(file))
        {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return bytesToHex(digest.digest());
    }

    /**
     * Converte bytes para representação hexadecimal
     */
    private String bytesToHex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes)
        {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Processa e reporta duplicados encontrados
     */
    private void processDuplicates(DuplicateRulesConfig rules, long scanTimeMs)
    {
        int duplicateGroups = 0;
        long totalWastedSpace = 0;
        int totalDuplicateFiles = 0;

        logger.info("DUPLICATE DETECTION: Analisando {} grupos de hash...", duplicateMap.size());

        //Processa cada grupo de arquivos com mesmo hash
        for (Map.Entry<String, List<FileInfo>> entry : duplicateMap.entrySet())
        {
            List<FileInfo> files = entry.getValue();

            //Grupo tem duplicados reais
            if (files.size() > 1)
            {
                duplicateGroups++;
                totalDuplicateFiles += files.size();

                //Ordena por data (mais recente primeiro) para estratégia NEWEST
                files.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

                long fileSize = files.get(0).size;
                long wastedSpace = fileSize * (files.size() - 1); // -1 porque um deve ser mantido
                totalWastedSpace += wastedSpace;

                logger.warn("DUPLICADOS ENCONTRADOS ({}): {} MB desperdiçados",
                        files.size(), wastedSpace / (1024 * 1024));

                //Log dos caminhos dos duplicados
                for (FileInfo file : files)
                {
                    logger.warn("  - {}", file.path);
                }

                //Processamento automático se habilitado
                if (rules != null && rules.isAutoRemove())
                {
                    handleAutomaticDuplicateRemoval(files, rules);
                }
            }
        }

        //Relatório final
        logger.info("=== RELATÓRIO DE DUPLICADOS ===");
        logger.info("Arquivos escaneados: {}", totalScannedFiles.get());
        logger.info("Espaço total escaneado: {} MB", totalScannedSize.get() / (1024 * 1024));
        logger.info("Grupos de duplicados: {}", duplicateGroups);
        logger.info("Arquivos duplicados: {}", totalDuplicateFiles);
        logger.info("Espaço desperdiçado: {} MB", totalWastedSpace / (1024 * 1024));
        logger.info("Tempo de escaneamento: {}ms", scanTimeMs);

        //Calcula percentual de duplicação
        if (totalScannedSize.get() > 0)
        {
            double duplicatePercentage = (totalWastedSpace * 100.0) / totalScannedSize.get();
            logger.info("Percentual de duplicação: {:.1f}%", duplicatePercentage);
        }
    }

    /**
     * Processa remoção automática de duplicados
     */
    private void handleAutomaticDuplicateRemoval(List<FileInfo> files, DuplicateRulesConfig rules)
    {
        //Define qual arquivo manter baseado na estratégia
        FileInfo fileToKeep = selectFileToKeep(files, rules.getKeepStrategy());

        for (FileInfo file : files)
        {
            //Pula o arquivo que deve ser mantido
            if (file.equals(fileToKeep))
            {
                continue;
            }

            try
            {
                //Move para pasta de duplicados ou remove
                if (rules.getDuplicatesDestination() != null && !rules.getDuplicatesDestination().isEmpty())
                {
                    moveDuplicateToDestination(file.path, rules.getDuplicatesDestination());
                }
                else
                {
                    Files.delete(file.path);
                    logger.info("DUPLICATE REMOVED: {}", file.path);
                }
            }
            catch (IOException e)
            {
                logger.error("DUPLICATE REMOVAL FAILED: {}", file.path, e);
            }
        }
    }

    /**
     * Seleciona arquivo a ser mantido baseado na estratégia
     */
    private FileInfo selectFileToKeep(List<FileInfo> files, String strategy)
    {
        return switch (strategy != null ? strategy.toUpperCase() : "NEWEST")
        {
            case "OLDEST" -> files.stream().min(Comparator.comparingLong(f -> f.lastModified)).orElse(files.get(0));
            case "NEWEST" -> files.stream().max(Comparator.comparingLong(f -> f.lastModified)).orElse(files.get(0));
            default -> files.get(0); // MANUAL - mantém o primeiro
        };
    }

    /**
     * Move duplicado para pasta de destino
     */
    private void moveDuplicateToDestination(Path source, String destinationBase) throws IOException
    {
        Path destination = Paths.get(destinationBase);

        //Cria diretório se não existir
        if (Files.notExists(destination))
        {
            Files.createDirectories(destination);
        }

        Path target = destination.resolve(source.getFileName());

        //Gera nome único se necessário
        int counter = 1;
        while (Files.exists(target))
        {
            String fileName = source.getFileName().toString();
            String baseName = fileName;
            String extension = "";

            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0)
            {
                baseName = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            }

            target = destination.resolve(baseName + "_dup" + counter + extension);
            counter++;
        }

        Files.move(source, target);
        logger.info("DUPLICATE MOVED: {} -> {}", source, target);
    }

    /**
     * Classe para armazenar informações de arquivo
     */
    private record FileInfo(Path path, long size, long lastModified)
    {
        //A classe record gera automaticamente os getters, equals, hashCode, toString e o construtor.
    }
}