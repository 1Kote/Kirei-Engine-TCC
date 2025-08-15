package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import br.com.tcc.kireiengine.config.model.SeisoConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
                logger.info("[TAREFA AGENDADA] Executando verificação de Seiri...");

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
                logger.info("[TAREFA AGENDADA] Executando verificação de Seisō...");

            };
            scheduler.scheduleAtFixedRate(seisoTask, seisoConfig.getInitialDelay(), seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
            logger.info("Tarefa de Seisō agendada para executar a cada {} {}", seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
        } else
        {
            logger.info("Tarefa de Seisō está desabilitada na configuração.");
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
        } catch (InterruptedException e)
        {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Agendador de tarefas encerrado.");
    }
}