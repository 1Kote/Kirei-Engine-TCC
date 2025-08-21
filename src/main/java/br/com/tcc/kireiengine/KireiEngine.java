package br.com.tcc.kireiengine;

import br.com.tcc.kireiengine.config.ConfigLoader;
import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.MoveFilesRule;
import br.com.tcc.kireiengine.config.model.SeiriConfig;
import br.com.tcc.kireiengine.config.model.SeitonRule;
import br.com.tcc.kireiengine.service.FileWatcherService;
import br.com.tcc.kireiengine.service.ScheduledTaskService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Classe principal e ponto de entrada da aplicação Kirei Engine.
 * Orquestra o ciclo de vida da aplicação, carrega e valida a configuração,
 * e inicia os serviços de monitoramento e tarefas agendadas.
 */
public class KireiEngine
{
    // Define o logger para esta classe, para registrar todos os eventos.
    private static final Logger logger = LogManager.getLogger(KireiEngine.class);

    // Define o caminho constante para o arquivo de configuração.
    private static final String CONFIG_FILE_PATH = "src/main/resources/config.json";

    // Declara os serviços que serão gerenciados pela aplicação.
    private static ScheduledTaskService scheduledTaskService;
    private static FileWatcherService fileWatcherService;

    /**
     * Ponto de entrada principal da aplicação (main).
     * @param args Argumentos de linha de comando (não utilizados).
     */
    public static void main(String[] args)
    {
        // Log inicial para indicar que o serviço está a arrancar.
        logger.info("========================================");
        logger.info("    Kirei Engine - Iniciando Serviço    ");
        logger.info("========================================");

        // Chama o ConfigLoader para carregar as configurações do arquivo JSON.
        Configuration config = ConfigLoader.loadConfig(CONFIG_FILE_PATH);

        // Condicional para verificar se o carregamento do arquivo falhou (retornou null).
        if (config == null)
        {
            // Se falhou, loga um erro fatal.
            logger.fatal("Não foi possível carregar o arquivo de configuração. Encerrando o serviço.");
            // Encerra a aplicação com um código de status de erro.
            System.exit(1);
        }

        // Condicional para chamar nosso método de validação da lógica da configuração.
        if (!validateConfiguration(config))
        {
            // Se a validação falhou, loga um erro fatal.
            logger.fatal("Configuração inválida encontrada. Por favor, corrija os erros apontados. Encerrando o serviço.");
            // Encerra a aplicação com um código de status de erro.
            System.exit(1);
        }

        // Log de sucesso, informando que a configuração foi carregada e quantas regras de Seiton foram encontradas.
        logger.info("Configuração carregada e validada com sucesso! {} regras de Seiton encontradas.", config.getSeitonRules().size());

        // Cria uma nova instância do serviço de tarefas agendadas, passando a configuração.
        scheduledTaskService = new ScheduledTaskService(config);
        // Cria uma nova instância do serviço de monitoramento em tempo real, passando a configuração.
        fileWatcherService = new FileWatcherService(config);

        // Chama o método que registra o Shutdown Hook para um encerramento limpo.
        registerShutdownHook();

        // Log para indicar o início dos serviços.
        logger.info("Iniciando serviços do Kirei Engine...");

        // Inicia o agendador de tarefas (Seiri e Seisō).
        scheduledTaskService.startScheduler();
        // Log para confirmar que o serviço de agendamento iniciou.
        logger.info("Serviço de tarefas agendadas iniciado com sucesso.");

        // Inicia o monitoramento de arquivos (Seiton). Este método é bloqueante.
        logger.info("Iniciando monitoramento de arquivos...");
        // A execução da thread principal ficará "pausada" aqui, à espera de eventos.
        fileWatcherService.startWatching();

        // Este log só será alcançado se o loop de monitoramento for interrompido inesperadamente.
        logger.info("Kirei Engine foi encerrado.");
    }

    /**
     * Valida a lógica do objeto de configuração carregado.
     * @param config O objeto Configuration a ser validado.
     * @return true se a configuração for válida, false caso contrário.
     */
    private static boolean validateConfiguration(Configuration config)
    {
        // Log para indicar o início da validação.
        logger.info("Iniciando validação da lógica da configuração...");
        // Variável de controle, assume que a configuração é válida até que um erro seja encontrado.
        boolean isValid = true;

        // --- Validação das pastas a serem monitorizadas ---
        // Condicional para verificar se a lista de pastas de monitoramento é nula ou vazia.
        if (config.getMonitorFolders() == null || config.getMonitorFolders().isEmpty())
        {
            // Se for, loga um erro e marca a configuração como inválida.
            logger.error("VALIDAÇÃO FALHOU: A lista 'monitorFolders' não pode estar vazia no config.json.");
            isValid = false;
        } else
        {
            // Se houver pastas, itera sobre cada uma delas.
            for (String folderPath : config.getMonitorFolders())
            {
                // Converte a string do caminho para um objeto Path.
                Path path = Paths.get(folderPath);
                // Condicional para verificar se o caminho não existe ou não é um diretório.
                if (Files.notExists(path) || !Files.isDirectory(path))
                {
                    // Se for, loga um erro e marca a configuração como inválida.
                    logger.error("VALIDAÇÃO FALHOU: O diretório em 'monitorFolders' não existe ou não é válido: {}", folderPath);
                    isValid = false;
                }
            }
        }

        // --- Validação das regras de Seiton ---
        // Condicional para verificar se a lista de regras de Seiton é nula ou vazia.
        if (config.getSeitonRules() == null || config.getSeitonRules().isEmpty())
        {
            // Se for, emite um aviso, pois a aplicação pode rodar, mas o Seiton não funcionará.
            logger.warn("VALIDAÇÃO AVISO: Nenhuma 'seitonRules' encontrada. A organização em tempo real (Seiton) não funcionará.");
        } else
        {
            // Se houver regras, itera sobre cada uma.
            for (SeitonRule rule : config.getSeitonRules())
            {
                // Condicional para verificar se o destino da regra é nulo ou está em branco.
                if (rule.getDestination() == null || rule.getDestination().isBlank())
                {
                    // Se for, loga um erro e marca a configuração como inválida.
                    logger.error("VALIDAÇÃO FALHOU: A regra de Seiton '{}' tem um destino (destination) inválido.", rule.getName());
                    isValid = false;
                }
            }
        }

        // --- Validação das regras de Seiri ---
        // Obtém o objeto de configuração do Seiri.
        SeiriConfig seiriConfig = config.getSeiriConfig();
        // Condicional para verificar se a configuração do Seiri existe e se o módulo está habilitado.
        if (seiriConfig != null && seiriConfig.isEnabled())
        {
            // Condicional para verificar se as regras internas do Seiri não foram definidas.
            if (seiriConfig.getRules() == null || seiriConfig.getRules().getMoveFilesNotAccessedForDays() == null)
            {
                // Se não foram, loga um erro e marca a configuração como inválida.
                logger.error("VALIDAÇÃO FALHOU: O módulo Seiri está habilitado, mas nenhuma regra 'moveFilesNotAccessedForDays' foi definida.");
                isValid = false;
            } else
            {
                // Se as regras existem, obtém a regra específica de mover arquivos antigos.
                MoveFilesRule rule = seiriConfig.getRules().getMoveFilesNotAccessedForDays();
                // Condicional para verificar se a regra está habilitada, mas o seu destino é inválido.
                if (rule.isEnabled() && (rule.getDestination() == null || rule.getDestination().isBlank()))
                {
                    // Se for, loga um erro e marca a configuração como inválida.
                    logger.error("VALIDAÇÃO FALHOU: A regra 'moveFilesNotAccessedForDays' do Seiri está habilitada, mas tem um destino inválido.");
                    isValid = false;
                }
            }
        }

        // Condicional final para logar o sucesso da validação.
        if (isValid)
        {
            logger.info("Validação da configuração concluída com sucesso.");
        }
        // Retorna o status final da validação.
        return isValid;
    }

    /**
     * Registra um "Shutdown Hook" na JVM para garantir um encerramento limpo.
     */
    private static void registerShutdownHook()
    {
        // Obtém a instância do Runtime da JVM e adiciona uma nova thread de encerramento.
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            // Log para indicar que o processo de encerramento começou.
            logger.info("========================================");
            logger.info("    Kirei Engine - Encerrando Serviço     ");
            logger.info("========================================");

            // Condicional para verificar se o serviço de monitoramento foi inicializado.
            if (fileWatcherService != null)
            {
                // Se foi, chama o método para parar o monitoramento.
                fileWatcherService.stopWatching();
            }
            // Condicional para verificar se o serviço de agendamento foi inicializado.
            if (scheduledTaskService != null)
            {
                // Se foi, chama o método para parar o agendador de forma graciosa.
                scheduledTaskService.stopScheduler();
            }
        }));
    }
}