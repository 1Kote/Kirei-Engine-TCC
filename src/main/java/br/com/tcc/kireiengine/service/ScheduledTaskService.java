package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import br.com.tcc.kireiengine.config.model.SeisoConfig;
import br.com.tcc.kireiengine.strategy.ScheduledTaskStrategy;
import br.com.tcc.kireiengine.strategy.SeiriOldFilesStrategy;
// (Futuramente, importaremos aqui as estratégias de Seisō)
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Serviço responsável por agendar e executar tarefas periódicas (Seiri, Seisō).
 * Utiliza o Padrão Strategy para desacoplar a lógica de agendamento da lógica
 * de execução das regras de negócio.
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
        // Adiciona a estratégia de verificação de arquivos antigos à lista de tarefas do Seiri.
        this.seiriTasks.add(new SeiriOldFilesStrategy());
        // (Futuramente, adicionaremos aqui as estratégias do Seisō, como new SeisoEmptyTrashStrategy()).
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
        // Condicional para verificar se o módulo Seiri está habilitado na configuração.
        if (seiriConfig != null && seiriConfig.isEnabled())
        {
            // Cria um Runnable (uma tarefa) que irá executar TODAS as estratégias da lista seiriTasks.
            Runnable seiriMasterTask = () ->
            {
                // Log de início da execução do conjunto de tarefas Seiri.
                logger.info("[AGENDADOR] Executando conjunto de tarefas Seiri...");
                // Itera sobre cada estratégia na lista de tarefas Seiri.
                for (ScheduledTaskStrategy task : seiriTasks)
                {
                    // Manda a estratégia executar sua lógica.
                    task.execute(config);
                }
            };

            // Agenda a tarefa principal do Seiri para ser executada nos intervalos definidos no config.json.
            scheduler.scheduleAtFixedRate(seiriMasterTask, seiriConfig.getInitialDelay(), seiriConfig.getPeriod(), seiriConfig.getTimeUnit());
            // Log para confirmar o agendamento.
            logger.info("Conjunto de tarefas Seiri agendado para executar a cada {} {}", seiriConfig.getPeriod(), seiriConfig.getTimeUnit());
        } else
        {
            // Log para informar que o módulo Seiri está desabilitado.
            logger.info("Módulo de tarefas Seiri está desabilitado na configuração.");
        }
    }

    /**
     * Agenda a execução de todas as estratégias de Seisō.
     */
    private void scheduleSeisoTasks()
    {
        // Obtém a configuração específica do Seisō.
        SeisoConfig seisoConfig = config.getSeisoConfig();
        // Condicional para verificar se o módulo Seisō está habilitado na configuração.
        if (seisoConfig != null && seisoConfig.isEnabled())
        {
            // Cria um Runnable que irá executar TODAS as estratégias da lista seisoTasks.
            Runnable seisoMasterTask = () ->
            {
                // Log de início da execução do conjunto de tarefas Seisō.
                logger.info("[AGENDADOR] Executando conjunto de tarefas Seisō...");
                // Itera sobre cada estratégia na lista de tarefas Seisō.
                for (ScheduledTaskStrategy task : seisoTasks)
                {
                    // Manda a estratégia executar sua lógica.
                    task.execute(config);
                }
            };
            // Agenda a tarefa principal do Seisō.
            scheduler.scheduleAtFixedRate(seisoMasterTask, seisoConfig.getInitialDelay(), seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
            // Log para confirmar o agendamento.
            logger.info("Conjunto de tarefas Seisō agendado para executar a cada {} {}", seisoConfig.getPeriod(), seisoConfig.getTimeUnit());
        } else
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
    }
}