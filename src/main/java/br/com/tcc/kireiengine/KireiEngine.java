package br.com.tcc.kireiengine;

import br.com.tcc.kireiengine.config.ConfigLoader;
import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.service.FileWatcherService;
import br.com.tcc.kireiengine.service.ScheduledTaskService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class KireiEngine
{
    private static final Logger logger = LogManager.getLogger(KireiEngine.class);
    private static final String CONFIG_FILE_PATH = "src/main/resources/config.json";
    public static void main(String[] args)
    {
        final ScheduledTaskService scheduledTaskService;

        logger.info("========================================");
        logger.info("    Kirei Engine - Iniciando Serviço    ");
        logger.info("========================================");

        //Carrega a configuração do config.json
        Configuration config = ConfigLoader.loadConfig(CONFIG_FILE_PATH);

        //Validação da configuração
        if (config == null)
        {
            logger.fatal("Não foi possivel carregar o arquivo de configuração. Encerrando o Serviço.");
            //Encerra a aplicação
            System.exit(1);
        }

        scheduledTaskService = new ScheduledTaskService(config);
        scheduledTaskService.startScheduler();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            logger.info("========================================");
            logger.info("    Kirei Engine - Encerrando Serviço     ");
            logger.info("========================================");
            
            if (scheduledTaskService != null)
            {
                scheduledTaskService.stopScheduler();
            }
        }));

        logger.info("Configuração carregada com sucesso! {} regras de Seiton encontradas", config.getSeitonRules().size());

        FileWatcherService watcherService = new FileWatcherService(config);
        watcherService.startWatching();


        logger.info("Kirei Engine foi encerrado de forma inesperada.");

    }
}