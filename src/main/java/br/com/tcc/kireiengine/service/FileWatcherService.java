package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.strategy.RuleStrategy;
import br.com.tcc.kireiengine.strategy.SeitonExtensionStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável por monitorar os diretórios em tempo real.
 * Utiliza o Padrão Strategy para aplicar regras de forma flexível aos novos arquivos.
 */
public class FileWatcherService
{
    // Define o logger para registrar todos os eventos deste serviço.
    private static final Logger logger = LogManager.getLogger(FileWatcherService.class);
    // Armazena a configuração carregada para ter acesso às regras.
    private final Configuration config;
    // Mapeia cada 'WatchKey' (uma chave de monitoramento) ao seu respectivo caminho (Path).
    private final Map<WatchKey, Path> watchKeyToPath = new HashMap<>();
    // Variável de controle para o loop principal. 'volatile' garante visibilidade entre threads.
    private volatile boolean running = false;
    // Lista para armazenar e gerenciar todas as estratégias de regras do Seiton.
    private final List<RuleStrategy> seitonStrategies = new ArrayList<>();

    /**
     * Construtor do serviço.
     * @param config O objeto de configuração.
     */
    public FileWatcherService(Configuration config)
    {
        // Atribui a configuração recebida à variável da classe.
        this.config = config;
        // Inicializa e adiciona todas as estratégias de Seiton que queremos usar.
        this.seitonStrategies.add(new SeitonExtensionStrategy(config));
        // (No futuro, para adicionar uma nova regra, bastaria adicionar uma nova estratégia aqui).
    }

    /**
     * Inicia o serviço de monitoramento. Contém o loop principal
     * que aguarda por eventos de criação de arquivos.
     */
    public void startWatching()
    {
        // Log para indicar o início do serviço.
        logger.info("Iniciando o serviço de monitoramento de arquivos (com Strategy)...");
        // Bloco try-with-resources para garantir que o WatchService seja fechado automaticamente.
        try(WatchService watchService = FileSystems.getDefault().newWatchService())
        {
            // Itera sobre cada caminho de pasta definido no config.json.
            for (String folderPath : config.getMonitorFolders())
            {
                // Converte a string do caminho para um objeto Path.
                Path path = Paths.get(folderPath);
                // Condicional para verificar se o caminho é um diretório válido.
                if(Files.isDirectory(path))
                {
                    // Registra o diretório no WatchService para monitorar eventos de criação.
                    WatchKey key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                    // Adiciona a chave e o caminho ao nosso mapa para referência futura.
                    watchKeyToPath.put(key, path);
                    // Log para confirmar qual diretório está sendo monitorado.
                    logger.info("Monitorando o diretorio: {}", path);
                }
                else
                {
                    // Log de aviso se o caminho não for um diretório válido.
                    logger.warn("O caminho do diretorio '{}' não é valido e será ignorado.", folderPath);
                }
            }

            // Log para indicar que o serviço está pronto e aguardando eventos.
            logger.info("Aguardando por novos arquivos...");
            // Define a variável de controle do loop como true.
            running = true;

            // Loop principal que mantém o serviço em execução.
            while (running)
            {
                // Variável para armazenar a chave do evento.
                WatchKey key;
                try
                {
                    // Aguarda aqui (bloqueia a thread) até que um evento ocorra.
                    key = watchService.take();
                }
                // Captura a exceção se a thread for interrompida.
                catch (InterruptedException e)
                {
                    // Log para informar que o serviço foi interrompido.
                    logger.info("Serviço de monitoramento interrompido.");
                    // Restaura o status de interrupção da thread.
                    Thread.currentThread().interrupt();
                    // Sai do loop.
                    break;
                }

                // Recupera o caminho do diretório onde o evento ocorreu.
                Path directory = watchKeyToPath.get(key);
                // Condicional para verificar se o diretório foi encontrado no mapa.
                if (directory == null)
                {
                    // Se não foi, reseta a chave e continua para o próximo evento.
                    key.reset();
                    continue;
                }

                // Itera sobre a lista de eventos recebidos para esta chave.
                for(WatchEvent<?> event : key.pollEvents())
                {
                    // Condicional para verificar se o evento é de criação de um novo arquivo/pasta.
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE)
                    {
                        // Se for, chama nosso método de lógica que aplica as estratégias.
                        handleFileEvent(event, directory);
                    }
                }

                // Reseta a chave para que ela possa receber novos eventos.
                boolean valid = key.reset();
                // Condicional para verificar se a chave ainda é válida.
                if (!valid)
                {
                    // Se não for, remove a chave do mapa.
                    watchKeyToPath.remove(key);
                }
            }
        }
        // Captura erros de I/O que possam ocorrer.
        catch (IOException e)
        {
            logger.error("Ocorreu um erro de I/O no serviço de monitoração.", e);
        }
        // Bloco 'finally' que é executado sempre no final.
        finally
        {
            // Define a variável de controle como false.
            running = false;
            // Log para informar que o serviço foi encerrado.
            logger.info("Serviço de monitoramento encerrado.");
        }
    }

    /**
     * Sinaliza ao serviço para parar o loop de monitoramento.
     */
    public void stopWatching()
    {
        // Log para indicar o início do processo de parada.
        logger.info("Parando o serviço de monitoramento...");
        // Altera a variável de controle do loop para false.
        running = false;
    }

    /**
     * Processa um evento de criação de arquivo, aplicando todas as estratégias de Seiton.
     * @param event O evento detectado.
     * @param directory O diretório onde o evento ocorreu.
     */
    private void handleFileEvent(WatchEvent<?> event, Path directory)
    {
        try
        {
            // Pausa a execução por um breve momento para garantir a escrita completa do arquivo.
            Thread.sleep(500);
            // Constrói o caminho completo para o novo arquivo.
            Path fullPath = directory.resolve((Path) event.context());

            // Condicional para verificar se o caminho corresponde a um arquivo regular (e não uma pasta).
            if (!Files.isRegularFile(fullPath))
            {
                // Se não for, encerra o método.
                return;
            }

            // Log para informar qual arquivo está sendo processado.
            logger.info("Processando novo arquivo: {}", fullPath);

            // Itera sobre a nossa lista de estratégias de regras.
            for (RuleStrategy strategy : seitonStrategies)
            {
                // Manda cada estratégia executar a sua lógica no arquivo.
                strategy.apply(fullPath);
            }
        }
        // Captura a exceção se a thread for interrompida durante o sleep.
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            logger.error("Thread interrompida durante processamento do arquivo.", e);
        }
        // Captura qualquer outro erro inesperado.
        catch (Exception e)
        {
            logger.error("Erro inesperado ao processar arquivo: {}", event.context(), e);
        }
    }
}