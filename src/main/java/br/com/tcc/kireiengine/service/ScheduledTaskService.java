package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import br.com.tcc.kireiengine.config.model.SeisoConfig;
import br.com.tcc.kireiengine.strategy.ScheduledTaskStrategy;
import br.com.tcc.kireiengine.strategy.SeiriOldFilesStrategy;
import br.com.tcc.kireiengine.strategy.SeisoTempFoldersStrategy;
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
    // Logger para registrar as operações deste serviço.
    private static final Logger logger = LogManager.getLogger(ScheduledTaskService.class);
    // Armazena a configuração completa da aplicação.
    private final Configuration config;
    // Serviço do Java para executar tarefas de forma agendada em uma thread de background.
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // Lista para armazenar as estratégias de tarefas do Seiri.
    private final List<ScheduledTaskStrategy> seiriTasks = new ArrayList<>();
    // Lista para armazenar as estratégias de tarefas do Seisō.
    private final List<ScheduledTaskStrategy> seisoTasks = new ArrayList<>();

    /**
     * Construtor do serviço.
     * @param config O objeto de configuração.
     */
    public ScheduledTaskService(Configuration config)
    {
        // Atribui a configuração recebida à variável da classe.
        this.config = config;
        // Chama o método para popular as listas de estratégias.
        initializeStrategies();
    }

    /**
     * Inicializa e popula as listas de estratégias de tarefas.
     * É aqui que "instalamos" as regras de negócio que o agendador irá executar.
     */
    private void initializeStrategies()
    {
        this.seiriTasks.add(new SeiriOldFilesStrategy());
        this.seisoTasks.add(new SeisoTempFoldersStrategy()); // CORREÇÃO: Adiciona strategy faltante
    }

    /**
     * Inicia o agendador de tarefas.
     * Lê as configurações de agendamento do config.json e programa a execução
     * das listas de estratégias.
     */
    public void startScheduler()
    {
        // Log para indicar o início do serviço de agendamento.
        logger.info("Iniciando o agendador de tarefas (com Strategy)...");
        // Chama os métodos para agendar as tarefas de Seiri e Seisō.
        scheduleSeiriTasks();
        scheduleSeisoTasks();
    }

    /**
     * Agenda a execução de todas as estratégias de Seiri.
     */
    private void scheduleSeiriTasks()
    {
        // Obtém a configuração específica do Seiri.
        SeiriConfig seiriConfig = config.getSeiriConfig();

        //Validação se Seiri está habilitado
        if (seiriConfig != null && seiriConfig.isEnabled())
        {
            // Cria um Runnable (uma tarefa) que irá executar TODAS as estratégias da lista seiriTasks.
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
        // Obtém a configuração específica do Seisō.
        SeisoConfig seisoConfig = config.getSeisoConfig();

        //Validação se Seiso está habilitado
        if (seisoConfig != null && seisoConfig.isEnabled())
        {
            // Cria um Runnable que irá executar TODAS as estratégias da lista seisoTasks.
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
            // Log para informar que o módulo Seisō está desabilitado.
            logger.info("Módulo de tarefas Seisō está desabilitado na configuração.");
        }
    }

    /**
     * Encerra o serviço de agendamento.
     */
    public void stopScheduler()
    {
        // Log para indicar o início do processo de encerramento.
        logger.info("Encerrando o agendador de tarefas...");
        // Comando para encerrar o scheduler.
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
}