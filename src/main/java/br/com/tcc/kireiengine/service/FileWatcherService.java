package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeitonRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public class FileWatcherService
{
    private static final Logger logger = LogManager.getLogger(FileWatcherService.class);
    private final Configuration config;

    public FileWatcherService(Configuration config)
    {
        this.config = config;
    }

    public void startWatching()
    {
        logger.info("Iniciando o serviço de monitoramento de arquivos...");

        try(WatchService watchService = FileSystems.getDefault().newWatchService())
        {
            for (String folderPath : config.getMonitorFolders())
            {
                Path path = Paths.get(folderPath);
                if(Files.isDirectory(path))
                {
                    path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                    logger.info("Monitorando o diretorio {}", path);
                }else
                {
                    logger.warn("O caminho do diretorio {} não é valido e será ignorado.", folderPath);
                }
            }

            logger.info("Aguardando por novos arquivos...");

            //Instancia de WatchKey
            WatchKey key;
            //Loop para tratamento de eventos
            while ((key = watchService.take()) != null) //.take() pausa a execução até que algum evento ocorra
            {
                //Percorre a lista de eventos
                for(WatchEvent<?> event : key.pollEvents())
                {
                    logger.info("Evento detectado: {} - Arquivo {}", event.kind(), event.context());
                }
                //Reseta e continua recebendo outros eventos
                key.reset();
            }

        }
        catch (IOException e)
        {
            logger.error("Ocorreu um erro de I/O no serviço de monitoração.", e);
        }
        catch (InterruptedException e)
        {
            logger.error("O serviço de monitoramento foi interrompido", e);
            Thread.currentThread().interrupt();
        }
    }

    private void handleFileEvent(WatchEvent<?> event, Path directory)
    {
        Path fileName = (Path) event.context();
        Path fullPath = directory.resolve(fileName);

        logger.info("Arquivo {} foi encontrado.", fullPath);

        String extension = getFileExtension(fullPath.toString()).orElse("");
        if(extension.isEmpty())
        {
            logger.warn("Não foi possivel determinar a extenção do arquivo: {}", fullPath);
            return;
        }

        for(SeitonRule rule : config.getSeitonRules())
        {
            if(rule.getExtensions().contains(extension))
            {
                logger.info("A regra correspondeus ao arquivo {}. Movendo...", rule.getName(), fullPath);
                moveFile(fullPath, rule.getDestination());
                return;
            }
        }
    }

    private void moveFile(Path source, String destinationFolder)
    {
        try {
            Path destinationPath = Paths.get(destinationFolder);
            // 3. Garante que o diretório de destino exista
            if (Files.notExists(destinationPath))
            {
                logger.info("Diretório de destino não existe. Criando: {}", destinationPath);
                Files.createDirectories(destinationPath);
            }

            Path target = destinationPath.resolve(source.getFileName());
            // 4. Move o arquivo
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("SUCESSO: Arquivo {} movido para {}", source.getFileName(), target);

        } catch (IOException e)
        {
            logger.error("FALHA: Não foi possível mover o arquivo {}.", source, e);
        }
    }

    private static Optional<String> getFileExtension(String fileName)
    {
        // Garante que o nome do arquivo não seja nulo
        return Optional.ofNullable(fileName)
                // Garante que o nome do arquivo contenha um ponto
                .filter(f -> f.contains("."))
                // Extrai a substring após o último ponto e a converte para minúsculas
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1).toLowerCase());
    }
}


