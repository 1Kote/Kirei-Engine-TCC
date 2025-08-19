package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScheduledTaskService
{

    private static final Logger logger = LogManager.getLogger(ScheduledTaskService.class);
    private final Configuration config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ScheduledTaskService(Configuration config)
    {
        this.config = config;
    }

    public void startScheduler()
    {
        logger.info("Iniciando o agendador de tarefas (Seiri & Seisō)...");
        scheduleSeiriTask();
        scheduleSeisoTask();
    }

    private void scheduleSeiriTask()
    {
        SeiriConfig seiriConfig = config.getSeiriConfig();
        if (seiriConfig != null && seiriConfig.isEnabled())
        {
            Runnable seiriTask = () ->
            {
                logger.info("[TAREFA AGENDADA] Iniciando verificação de Seiri...");
                checkAndMoveOldFiles(seiriConfig);
                logger.info("[TAREFA AGENDADA] Verificação de Seiri concluída.");
            };
            scheduler.scheduleAtFixedRate(seiriTask, seiriConfig.getInitialDelay(), seiriConfig.getPeriod(), seiriConfig.getTimeUnit());
            logger.info("Tarefa de Seiri agendada para executar a cada {} {}", seiriConfig.getPeriod(), seiriConfig.getTimeUnit());
        }
        else
        {
            logger.info("Tarefa de Seiri está desabilitada na configuração.");
        }
    }

    private void scheduleSeisoTask()
    {
        SeisoConfig seisoConfig = config.getSeisoConfig();
        if (seisoConfig != null && seisoConfig.isEnabled())
        {
            Runnable seisoTask = () ->
            {
                logger.info("[TAREFA AGENDADA] Iniciando verificação de Seisō...");
                cleanTemporaryFolders(seisoConfig);
                logger.info("[TAREFA AGENDADA] Verificação de Seisō concluída.");
            };
            scheduler.scheduleAtFixedRate(seisoTask, seisoConfig.getInitialDelay(), seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
            logger.info("Tarefa de Seisō agendada para executar a cada {} {}", seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
        }
        else
        {
            logger.info("Tarefa de Seisō está desabilitada na configuração.");
        }
    }

    private void checkAndMoveOldFiles(SeiriConfig seiriConfig)
    {
        if (seiriConfig.getRules() == null || seiriConfig.getRules().getMoveFilesNotAccessedForDays() == null)
        {
            logger.warn("SEIRI: Regras de Seiri não definidas no config.json. A verificação de ficheiros antigos será ignorada.");
            return;
        }

        MoveFilesRule rule = seiriConfig.getRules().getMoveFilesNotAccessedForDays();
        if (!rule.isEnabled())
        {
            logger.info("SEIRI: A regra para mover ficheiros antigos está desabilitada.");
            return;
        }

        long daysInMillis = TimeUnit.DAYS.toMillis(rule.getDays());
        long currentTime = System.currentTimeMillis();

        logger.info("SEIRI: Verificando ficheiros não acedidos há {} dias...", rule.getDays());

        for (String folderPath : config.getMonitorFolders())
        {
            try
            {
                Files.walk(Paths.get(folderPath))
                        .filter(Files::isRegularFile)
                        .forEach(path ->
                        {
                            try
                            {
                                long lastModifiedTime = Files.getLastModifiedTime(path).toMillis();
                                if ((currentTime - lastModifiedTime) > daysInMillis)
                                {
                                    logger.info("SEIRI: Ficheiro {} é considerado antigo. Movendo...", path);
                                    moveFile(path, rule.getDestination());
                                }
                            } catch (IOException e)
                            {
                                logger.error("SEIRI: Erro ao verificar o ficheiro {}", path, e);
                            }
                        });
            } catch (IOException e)
            {
                logger.error("SEIRI: Erro ao percorrer o diretório {}", folderPath, e);
            }
        }
    }

    private void cleanTemporaryFolders(SeisoConfig seisoConfig)
    {
        if (seisoConfig.getRules() == null || seisoConfig.getRules().getCleanTemporaryFolders() == null) {
            logger.warn("SEISŌ: Regras de limpeza de pastas temporárias não definidas. A tarefa será ignorada.");
            return;
        }

        CleanTemporaryFoldersRule rule = seisoConfig.getRules().getCleanTemporaryFolders();
        if (!rule.isEnabled())
        {
            logger.info("SEISŌ: A regra para limpar pastas temporárias está desabilitada.");
            return;
        }

        logger.info("SEISŌ: Iniciando limpeza de pastas temporárias...");
        for (String folderPath : rule.getFolders())
        {
            Path path = Paths.get(folderPath);
            if (Files.notExists(path))
            {
                logger.warn("SEISŌ: O diretório especificado para limpeza não existe: {}", path);
                continue;
            }

            try
            {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(path))
                        .forEach(p ->
                        {
                            try
                            {
                                Files.delete(p);
                                logger.info("SEISŌ: Apagado {}", p);
                            } catch (IOException e)
                            {
                                logger.error("SEISŌ: Falha ao apagar {}. Pode estar em uso.", p);
                            }
                        });
                logger.info("SEISŌ: Limpeza concluída para a pasta {}", path);
            } catch (IOException e)
            {
                logger.error("SEISŌ: Erro ao tentar limpar a pasta {}", path, e);
            }
        }
    }

    private void moveFile(Path source, String destinationFolder)
    {
        try
        {
            Path destinationPath = Paths.get(destinationFolder);
            if (Files.notExists(destinationPath))
            {
                Files.createDirectories(destinationPath);
            }
            Path target = destinationPath.resolve(source.getFileName());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("SUCESSO: Ficheiro {} movido para {}", source.getFileName(), target);
        } catch (IOException e)
        {
            logger.error("FALHA: Não foi possível mover o ficheiro {}.", source, e);
        }
    }

    public void stopScheduler()
    {
        logger.info("Encerrando o agendador de tarefas...");
        scheduler.shutdown();
        try
        {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS))
            {
                scheduler.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Agendador de tarefas encerrado.");
    }
}