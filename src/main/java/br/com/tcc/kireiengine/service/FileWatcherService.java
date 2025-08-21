package br.com.tcc.kireiengine.service;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeitonRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço responsável por monitorar os diretórios especificados na configuração
 * em tempo real, aplicando as regras de Seiton (Organização).
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

    /**
     * Construtor do serviço.
     * @param config O objeto de configuração que contém todas as regras.
     */
    public FileWatcherService(Configuration config)
    {
        this.config = config;
    }

    /**
     * Inicia o serviço de monitoramento. Este método contém o loop principal
     * que aguarda por eventos de criação de arquivos nos diretórios.
     */
    public void startWatching()
    {
        // Log para indicar o início do serviço.
        logger.info("Iniciando o serviço de monitoramento de arquivos...");

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
                    // Registra o diretório no WatchService para monitorar eventos de criação de arquivos.
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
                // Captura a exceção se a thread for interrompida (ex: no encerramento da aplicação).
                catch (InterruptedException e)
                {
                    // Log para informar que o serviço foi interrompido.
                    logger.info("Serviço de monitoramento interrompido.");
                    // Boa prática: restaura o status de interrupção da thread.
                    Thread.currentThread().interrupt();
                    // Sai do loop.
                    break;
                }

                // Recupera o caminho do diretório onde o evento ocorreu, usando o mapa.
                Path directory = watchKeyToPath.get(key);
                // Condicional para verificar se o diretório foi encontrado no mapa.
                if (directory == null)
                {
                    // Se não foi, loga um aviso e continua para o próximo evento.
                    logger.warn("Diretório não encontrado para a chave de monitoramento.");
                    key.reset();
                    continue;
                }

                // Itera sobre a lista de eventos recebidos para esta chave.
                for(WatchEvent<?> event : key.pollEvents())
                {
                    // Condicional para verificar se ocorreu um evento de 'OVERFLOW'.
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW)
                    {
                        // Se ocorreu, significa que eventos podem ter sido perdidos. Loga um aviso.
                        logger.warn("Overflow detectado - alguns eventos podem ter sido perdidos.");
                        continue;
                    }

                    // Condicional para verificar se o evento é de criação de um novo arquivo/pasta.
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE)
                    {
                        // Se for, chama nosso método de lógica para processar o arquivo.
                        handleFileEvent(event, directory);
                    }
                }

                // Reseta a chave para que ela possa receber novos eventos. Essencial!
                boolean valid = key.reset();
                // Condicional para verificar se a chave ainda é válida (ex: o diretório não foi apagado).
                if (!valid)
                {
                    // Se não for, loga um aviso, remove a chave do mapa e sai do loop.
                    logger.warn("Chave de monitoramento tornou-se inválida para: {}", directory);
                    watchKeyToPath.remove(key);
                    break;
                }
            }
        }
        // Captura erros de I/O que possam ocorrer no serviço de monitoramento.
        catch (IOException e)
        {
            logger.error("Ocorreu um erro de I/O no serviço de monitoração.", e);
        }
        // Bloco 'finally' que é executado sempre, garantindo que o status seja atualizado.
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
     * Chamado pelo Shutdown Hook para um encerramento gracioso.
     */
    public void stopWatching()
    {
        // Log para indicar o início do processo de parada.
        logger.info("Parando o serviço de monitoramento...");
        // Altera a variável de controle do loop para false.
        running = false;
    }

    /**
     * Processa um único evento de criação de arquivo.
     * @param event O evento detectado pelo WatchService.
     * @param directory O diretório onde o evento ocorreu.
     */
    private void handleFileEvent(WatchEvent<?> event, Path directory)
    {
        try
        {
            // Pausa a execução por um breve momento para garantir que o arquivo foi completamente escrito no disco.
            Thread.sleep(500);

            // Obtém o nome do arquivo a partir do contexto do evento.
            Path fileName = (Path) event.context();
            // Constrói o caminho completo para o novo arquivo.
            Path fullPath = directory.resolve(fileName);

            // Condicional para verificar se o arquivo ainda existe e se é um arquivo regular (não uma pasta).
            if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath))
            {
                // Se não for, loga uma mensagem de debug e encerra o método para este evento.
                logger.debug("Arquivo {} não existe ou não é um arquivo regular. Ignorando.", fullPath);
                return;
            }

            // Log para informar qual arquivo está sendo processado.
            logger.info("Processando arquivo: {}", fullPath);

            // Tenta extrair a extensão do arquivo. Se não houver, retorna uma string vazia.
            String extension = getFileExtension(fullPath.toString()).orElse("");
            // Condicional para verificar se a extensão foi encontrada.
            if(extension.isEmpty())
            {
                // Se não foi, loga um aviso e encerra o método.
                logger.warn("Não foi possível determinar a extensão do arquivo: {}", fullPath);
                return;
            }

            // Itera sobre cada regra de Seiton definida na configuração.
            for(SeitonRule rule : config.getSeitonRules())
            {
                // Condicional para verificar se a lista de extensões da regra contém a extensão do nosso arquivo.
                if(rule.getExtensions().contains(extension))
                {
                    // Se contém, loga a regra correspondente.
                    logger.info("Regra '{}' corresponde ao arquivo {}. Movendo...", rule.getName(), fullPath);
                    // Chama o método para mover o arquivo para o destino definido na regra.
                    moveFile(fullPath, rule.getDestination());
                    // Encerra o método, pois a primeira regra correspondente já foi aplicada.
                    return;
                }
            }
            // Log para informar se nenhuma regra foi encontrada para o tipo de arquivo.
            logger.info("Nenhuma regra de Seiton encontrada para a extensão '{}' do arquivo: {}", extension, fullPath);
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

    /**
     * Move um arquivo da origem para o destino, com tratamento de erros específico
     * e organização em subpasta baseada na extensão.
     * @param source O caminho completo do arquivo de origem.
     * @param baseDestinationFolder O caminho da pasta de destino principal.
     */
    private void moveFile(Path source, String baseDestinationFolder)
    {
        // Tenta extrair a extensão do nome do arquivo para usar como nome da subpasta.
        getFileExtension(source.toString()).ifPresent(extension ->
        {
            try
            {
                // Constrói o caminho completo da subpasta de destino (ex: /.../Documentos/PDF).
                Path destinationPath = Paths.get(baseDestinationFolder, extension.toUpperCase());

                // Condicional para verificar se a subpasta de destino não existe.
                if (Files.notExists(destinationPath))
                {
                    // Log para informar que a subpasta será criada.
                    logger.info("Subpasta de destino não existe. Criando: {}", destinationPath);
                    // Cria a estrutura completa de diretórios para a subpasta.
                    Files.createDirectories(destinationPath);
                }

                // Define o caminho final do arquivo dentro da subpasta de destino.
                Path target = destinationPath.resolve(source.getFileName());

                // Condicional para verificar se um arquivo com o mesmo nome já existe no destino.
                if (Files.exists(target))
                {
                    // Se o arquivo já existe, gera um novo nome único (ex: relatorio_1.pdf).
                    target = generateUniqueFileName(target);
                    // Log para informar que um novo nome foi gerado.
                    logger.info("Arquivo já existe no destino. Usando nome único: {}", target.getFileName());
                }

                // Move o arquivo da origem para o destino final.
                Files.move(source, target);
                // Log de sucesso, informando qual arquivo foi movido e para onde.
                logger.info("SUCESSO: Arquivo '{}' movido para '{}'", source.getFileName(), target);
            }
            // Captura erros específicos de segurança (ex: falta de permissão para ler/escrever).
            catch (SecurityException e)
            {
                logger.error("FALHA DE PERMISSÃO: Não foi possível mover '{}'. Verifique as permissões do sistema.", source, e);
            }
            // Captura erros específicos de I/O (ex: disco cheio, arquivo em uso por outro programa).
            catch (IOException e)
            {
                logger.error("FALHA DE I/O: Não foi possível mover '{}'. O arquivo pode estar em uso ou o disco cheio.", source, e);
            }
            // Captura qualquer outro erro inesperado que não foi previsto.
            catch (Exception e)
            {
                logger.error("FALHA INESPERADA: Ocorreu um erro não previsto ao mover o arquivo '{}'.", source, e);
            }
        });
    }

    /**
     * Gera um nome de arquivo único se o destino já contiver um arquivo com o mesmo nome.
     * @param target O caminho de destino original.
     * @return Um novo caminho de destino com um nome de arquivo único.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    private Path generateUniqueFileName(Path target) throws IOException
    {
        // Obtém o nome completo do arquivo (ex: "relatorio.pdf").
        String fileName = target.getFileName().toString();
        // Variáveis para armazenar o nome base e a extensão.
        String baseName;
        String extension;

        // Encontra a posição do último ponto no nome do arquivo.
        int dotIndex = fileName.lastIndexOf('.');
        // Condicional para verificar se o arquivo tem uma extensão.
        if (dotIndex > 0) {
            // Se tiver, separa o nome base (ex: "relatorio").
            baseName = fileName.substring(0, dotIndex);
            // E a extensão (ex: ".pdf").
            extension = fileName.substring(dotIndex);
        } else {
            // Se não tiver, o nome base é o nome completo do arquivo.
            baseName = fileName;
            // E a extensão é uma string vazia.
            extension = "";
        }

        // Inicia um contador para adicionar ao nome do arquivo.
        int counter = 1;
        // Variável para armazenar o novo caminho de destino.
        Path uniqueTarget;
        // Loop para tentar nomes diferentes até encontrar um que não exista.
        do {
            // Constrói o novo nome (ex: "relatorio_1.pdf").
            String uniqueName = baseName + "_" + counter + extension;
            // Cria o novo caminho completo.
            uniqueTarget = target.getParent().resolve(uniqueName);
            // Incrementa o contador para a próxima tentativa.
            counter++;
            // Continua o loop enquanto o arquivo já existir e o contador for menor que um limite de segurança.
        } while (Files.exists(uniqueTarget) && counter < 1000);

        // Retorna o novo caminho de destino único.
        return uniqueTarget;
    }

    /**
     * Extrai a extensão de um nome de arquivo.
     * @param fileName O nome completo do arquivo.
     * @return Um Optional contendo a extensão em minúsculas, ou vazio se não houver.
     */
    private static Optional<String> getFileExtension(String fileName)
    {
        // Retorna um Optional do nome do arquivo, que pode ser nulo.
        return Optional.ofNullable(fileName)
                // Garante que o nome do arquivo contém um ponto.
                .filter(f -> f.contains("."))
                // Se contiver, extrai a substring após o último ponto e a converte para minúsculas.
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1).toLowerCase());
    }
}