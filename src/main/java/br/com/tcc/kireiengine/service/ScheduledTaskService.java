package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import br.com.tcc.kireiengine.config.model.SeisoConfig;
import br.com.tcc.kireiengine.config.model.DuplicateDetectionConfig;
import br.com.tcc.kireiengine.strategy.ScheduledTaskStrategy;
import br.com.tcc.kireiengine.strategy.SeiriOldFilesStrategy;
import br.com.tcc.kireiengine.strategy.SeisoTempFoldersStrategy;
import br.com.tcc.kireiengine.strategy.DuplicateDetectionStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Serviço para agendar tarefas Seiri/Seiso usando Strategy Pattern
 */
public class ScheduledTaskService
{
    private static final Logger logger = LogManager.getLogger(ScheduledTaskService.class);
    private final Configuration config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // Pool para múltiplas tarefas
    private final List<ScheduledTaskStrategy> seiriTasks = new ArrayList<>();
    private final List<ScheduledTaskStrategy> seisoTasks = new ArrayList<>();
    private final List<ScheduledTaskStrategy> duplicateTasks = new ArrayList<>();

    public ScheduledTaskService(Configuration config)
    {
        this.config = config;
        initializeStrategies();
    }

    /**
     * Inicializa strategies disponíveis
     */
    private void initializeStrategies()
    {
        this.seiriTasks.add(new SeiriOldFilesStrategy());
        this.seisoTasks.add(new SeisoTempFoldersStrategy());
        this.duplicateTasks.add(new DuplicateDetectionStrategy());
    }

    public void startScheduler()
    {
        logger.info("Iniciando agendador de tarefas...");
        scheduleSeiriTasks();
        scheduleSeisoTasks();
        scheduleDuplicateTasks();
    }

    /**
     * Agenda tarefas Seiri
     */
    private void scheduleSeiriTasks()
    {
        SeiriConfig seiriConfig = config.getSeiriConfig();

        //Validação se Seiri está habilitado
        if (seiriConfig != null && seiriConfig.isEnabled())
        {
            Runnable seiriMasterTask = () ->
            {
                logger.info("[AGENDADOR] Executando tarefas Seiri...");

                //Execução de todas as strategies de Seiri
                for (ScheduledTaskStrategy task : seiriTasks)
                {
                    try
                    {
                        task.execute(config);
                    }
                    catch (Exception e)
                    {
                        logger.error("Erro executando tarefa Seiri: {}", task.getClass().getSimpleName(), e);
                    }
                }
            };

            scheduler.scheduleAtFixedRate(
                    seiriMasterTask,
                    seiriConfig.getInitialDelay(),
                    seiriConfig.getPeriod(),
                    seiriConfig.getTimeUnit()
            );

            logger.info("Seiri agendado: {} {}", seiriConfig.getPeriod(), seiriConfig.getTimeUnit());
        }
        else
        {
            logger.info("Seiri desabilitado.");
        }
    }

    /**
     * Agenda tarefas Seiso
     */
    private void scheduleSeisoTasks()
    {
        SeisoConfig seisoConfig = config.getSeisoConfig();

        //Validação se Seiso está habilitado
        if (seisoConfig != null && seisoConfig.isEnabled())
        {
            Runnable seisoMasterTask = () ->
            {
                logger.info("[AGENDADOR] Executando tarefas Seiso...");

                //Execução de todas as strategies de Seiso
                for (ScheduledTaskStrategy task : seisoTasks)
                {
                    try
                    {
                        task.execute(config);
                    }
                    catch (Exception e)
                    {
                        logger.error("Erro executando tarefa Seiso: {}", task.getClass().getSimpleName(), e);
                    }
                }
            };

            scheduler.scheduleAtFixedRate(
                    seisoMasterTask,
                    seisoConfig.getInitialDelay(),
                    seisoConfig.getPeriod(),
                    seisoConfig.getTimeUnit()
            );

            logger.info("Seiso agendado: {} {}", seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
        }
        else
        {
            logger.info("Seiso desabilitado.");
        }
    }

    /**
     * Para o agendador graciosamente
     */
    public void stopScheduler()
    {
        logger.info("Parando agendador...");
        scheduler.shutdown();

        try
        {
            //Aguarda 30s para tarefas terminarem
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS))
            {
                scheduler.shutdownNow();
                logger.warn("Agendador forçado a parar.");
            }
        }
        catch (InterruptedException e)
        {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Agendador encerrado.");
    }

    /**
     * Agenda tarefas de detecção de duplicados
     */
    private void scheduleDuplicateTasks()
    {
        DuplicateDetectionConfig duplicateConfig = config.getDuplicateDetectionConfig();

        //Validação se detecção está habilitada
        if (duplicateConfig != null && duplicateConfig.isEnabled())
        {
            Runnable duplicateTask = () ->
            {
                logger.info("[AGENDADOR] Executando detecção de duplicados...");

                //Execução de todas as strategies de duplicados
                for (ScheduledTaskStrategy task : duplicateTasks)
                {
                    try
                    {
                        task.execute(config);
                    }
                    catch (Exception e)
                    {
                        logger.error("Erro executando detecção de duplicados: {}", task.getClass().getSimpleName(), e);
                    }
                }
            };

            scheduler.scheduleAtFixedRate(
                    duplicateTask,
                    duplicateConfig.getInitialDelay(),
                    duplicateConfig.getPeriod(),
                    duplicateConfig.getTimeUnit()
            );

            logger.info("Detecção de duplicados agendada: {} {}", duplicateConfig.getPeriod(), duplicateConfig.getTimeUnit());
        }
        else
        {
            logger.info("Detecção de duplicados desabilitada.");
        }
    }
}